package com.symfony.demo.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Security controller — port of the PHP {@code App\Controller\SecurityController::login()}
 * ({@code #[Route('/login', name: 'security_login')]}), Requirements 7.1–7.4, 7.6.
 *
 * <p><b>Behavioural parity with the PHP action:</b>
 * <ul>
 *   <li>The PHP action first checks {@code $this->getUser()} and, when a user is already
 *       authenticated, redirects to {@code blog_index} ({@code /blog/}) — reproduced here by
 *       inspecting the {@link SecurityContextHolder} authentication and treating anything that is
 *       {@code null} or an {@link AnonymousAuthenticationToken} as "not logged in"
 *       (Requirement 7.6).</li>
 *   <li>Otherwise it renders {@code security/login.html.twig} with two variables taken from
 *       Symfony's {@code AuthenticationUtils}: {@code last_username} (the last submitted username)
 *       and {@code error} (the last {@code AuthenticationException}). Here those come from the
 *       Spring Security session attributes the default failure handler writes on a failed login
 *       redirect (Requirements 7.2, 7.3, 7.4).</li>
 * </ul>
 *
 * <p><b>How {@code last_username} / {@code error} are sourced:</b> Spring's default
 * {@code SimpleUrlAuthenticationFailureHandler} (active because {@code SecurityConfig} uses
 * {@code failureUrl("/login?error")}) stores the {@link AuthenticationException} in the session
 * under {@link WebAttributes#AUTHENTICATION_EXCEPTION}
 * ({@code "SPRING_SECURITY_LAST_EXCEPTION"}). The last username is read from the
 * {@code "SPRING_SECURITY_LAST_USERNAME"} session attribute (see {@link #LAST_USERNAME}). Both are
 * read defensively and may be absent (fresh visit,
 * or the container/filter did not populate the username), in which case {@code last_username}
 * falls back to an empty string and {@code error} to {@code null} — the template simply omits the
 * error alert. The exception attribute is removed after reading so a stale error does not survive a
 * page refresh, mirroring Symfony's one-shot {@code getLastAuthenticationError()} semantics.
 */
@Controller
public class SecurityController {

    /**
     * Session attribute holding the last submitted username. This is the historical Spring Security
     * key ({@code UsernamePasswordAuthenticationFilter.SPRING_SECURITY_LAST_USERNAME_KEY}); the
     * {@code WebAttributes.LAST_USERNAME} constant was removed in current Spring Security, so the
     * literal value is referenced directly. Read defensively — it may be absent.
     */
    private static final String LAST_USERNAME = "SPRING_SECURITY_LAST_USERNAME";

    /**
     * {@code GET /login} — renders the login page, or redirects an already-authenticated user to
     * the blog index.
     *
     * @param request the current request (used only to read the existing session, if any; never
     *                creates one)
     * @param model   the view model populated with {@code last_username} and {@code error}
     * @return {@code redirect:/blog/} when already authenticated, otherwise the
     *         {@code security/login} view
     */
    @GetMapping("/login")
    public String login(HttpServletRequest request, Model model) {
        // Requirement 7.6: an already-authenticated (non-anonymous) user never sees the login form.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/blog/";
        }

        String lastUsername = "";
        String error = null;

        // Read (without creating) the session the failure handler may have populated.
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object lastUsernameAttr = session.getAttribute(LAST_USERNAME);
            if (lastUsernameAttr != null) {
                lastUsername = lastUsernameAttr.toString();
            }

            Object exception = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            if (exception instanceof AuthenticationException authenticationException) {
                error = authenticationException.getMessage();
                // Consume the one-shot error so a refresh does not re-display it (parity with
                // Symfony's AuthenticationUtils::getLastAuthenticationError()).
                session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            }
        }

        model.addAttribute("last_username", lastUsername);
        model.addAttribute("error", error);
        return "security/login";
    }
}
