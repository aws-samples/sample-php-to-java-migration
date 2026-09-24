package com.symfony.demo.config;

import com.symfony.demo.security.PostPermissionEvaluator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration — port of the Symfony {@code config/packages/security.yaml}
 * {@code main} firewall, role hierarchy, and access control (Requirements 7.1–7.6, 8.1, 9.1,
 * 10.2).
 *
 * <p><b>Field-name / path parity with the PHP app</b> (from {@code security.yaml} +
 * {@code templates/security/login.html.twig}):
 * <ul>
 *   <li>Login page and processing both live at {@code /login} — Symfony sets both
 *       {@code login_path} and {@code check_path} to the {@code security_login} route
 *       ({@code #[Route('/login')]}).</li>
 *   <li>The Twig login form posts {@code _username} and {@code _password} (Symfony's default
 *       form-login parameter names), so {@link org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer#usernameParameter}
 *       / {@code passwordParameter} are set to those exact names.</li>
 *   <li>Remember-me uses the {@code _remember_me} checkbox (Symfony default), lifetime 604800s
 *       (1 week), matching {@code remember_me.lifetime}.</li>
 *   <li>{@code default_target_path: blog_index} &rarr; success URL {@code /blog/} (used only when
 *       there is no saved request; the "already-authenticated &rarr; blog_index" redirect and the
 *       controller's saved-target-path to the admin index are handled in the SecurityController,
 *       task 10.1).</li>
 *   <li>{@code logout.target: homepage} &rarr; logout success URL {@code /}.</li>
 * </ul>
 *
 * <p><b>CSRF token parameter deviation:</b> the Twig form used Symfony's {@code _csrf_token}
 * field with the {@code authenticate} token id. Spring Security manages CSRF with its own token
 * (default parameter {@code _csrf}); the login/logout templates authored in task 10.1 render
 * Spring's {@code ${_csrf}} token. CSRF protection itself is enabled (Spring default) for all
 * state-changing forms, matching Symfony's {@code enable_csrf: true} on login and logout. This
 * naming difference is a framework-mechanics detail (no behavioral parity impact) and is recorded
 * for BEHAVIOR_CHANGES.md.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Remember-me signing key. Mirrors Symfony's {@code remember_me.secret: '%kernel.secret%'},
     * which derives from the {@code APP_SECRET} env var. Externalized so deployments match the PHP
     * env approach (Requirement 15.1).
     */
    private final String rememberMeKey;

    public SecurityConfig(@Value("${app.secret:${APP_SECRET:symfony-demo-kitten-secret}}") String rememberMeKey) {
        this.rememberMeKey = rememberMeKey;
    }

    /**
     * Password encoder verifying the seeded {@code $2y$} bcrypt hashes of "kitten" from
     * {@code V2__seed.sql} (Requirement 7.5).
     *
     * <p>Symfony's {@code password_hashers: 'auto'} produced bcrypt hashes with the {@code $2y$}
     * prefix. Spring's {@link BCryptPasswordEncoder} verifies {@code $2a$}/{@code $2b$}/{@code $2y$}
     * hashes interchangeably, so the demo users (jane_admin / tom_admin / john_user, password
     * "kitten") authenticate identically with no re-hashing. A plain {@code BCryptPasswordEncoder}
     * is used rather than a {@code DelegatingPasswordEncoder}, because the stored hashes carry no
     * {@code {id}} algorithm prefix (a delegating encoder would reject unprefixed hashes).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Role hierarchy {@code ROLE_ADMIN > ROLE_USER}, port of {@code role_hierarchy: ROLE_ADMIN:
     * ROLE_USER}. Exposed as a bean so both web and method security apply the hierarchy: a
     * {@code ROLE_ADMIN} user satisfies {@code hasRole('USER')} checks (e.g. the {@code /profile}
     * routes).
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("USER")
                .build();
    }

    /**
     * Method-security expression handler that registers {@link PostPermissionEvaluator} so
     * {@code @PreAuthorize("hasPermission(#post, 'edit')")} resolves to the {@code PostVoter}
     * parity check (Requirement 10.1, 10.2, 9.5). The shared {@link RoleHierarchy} is applied here
     * too, so {@code hasRole}/{@code hasAuthority} checks in {@code @PreAuthorize} keep honoring
     * {@code ROLE_ADMIN > ROLE_USER} (a custom handler bean otherwise replaces the default and
     * would lose the hierarchy).
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            RoleHierarchy roleHierarchy, PostPermissionEvaluator postPermissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        handler.setPermissionEvaluator(postPermissionEvaluator);
        return handler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Static assets — parity with the Symfony `dev` firewall (security: false
                        // for ^/(assets|build)/) and public asset serving.
                        .requestMatchers(
                                "/assets/**", "/build/**", "/css/**", "/js/**", "/images/**",
                                "/favicon.ico", "/webjars/**", "/robots.txt")
                        .permitAll()
                        // Public login page (GET renders, POST processes).
                        .requestMatchers("/login").permitAll()
                        // Error dispatch must be public so 404/500 pages render for anonymous
                        // users (e.g. a 404 on an unknown public post slug — Requirement 4.2 —
                        // must return 404, not redirect to /login).
                        .requestMatchers("/error").permitAll()
                        // Public homepage + public blog (index, pagination, tag filter, RSS, post
                        // detail, search). Comment creation under /blog/comment/** is left to
                        // method security (@PreAuthorize on the controller, task 9.1), matching the
                        // PHP `#[IsGranted('IS_AUTHENTICATED')]` on commentNew.
                        .requestMatchers("/", "/blog", "/blog/", "/blog/**").permitAll()
                        // /profile/* requires ROLE_USER (Symfony `#[Route('/profile'),
                        // IsGranted(ROLE_USER)]`). ROLE_ADMIN inherits ROLE_USER via the hierarchy.
                        .requestMatchers("/profile/**").hasRole("USER")
                        // Admin blog CRUD requires ROLE_ADMIN (Symfony `#[Route('/admin/post')]`
                        // + `#[IsGranted(ROLE_ADMIN)]`).
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Form login mirroring the `main` firewall's form_login block.
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("_username")
                        .passwordParameter("_password")
                        // default_target_path: blog_index; honor a saved request when present.
                        .defaultSuccessUrl("/blog/", false)
                        .failureUrl("/login?error")
                        .permitAll())
                // remember_me: `_remember_me` checkbox, 1 week lifetime, signed with the app secret.
                .rememberMe(remember -> remember
                        .rememberMeParameter("_remember_me")
                        .key(rememberMeKey)
                        .tokenValiditySeconds(604800))
                // logout.target: homepage ('/'); CSRF-protected POST /logout (Spring default).
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "POST"))
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll());
        // CSRF stays enabled (Spring default) for all state-changing forms, matching Symfony's
        // enable_csrf on login/logout and the form CSRF tokens on comment/profile/admin forms.
        return http.build();
    }
}
