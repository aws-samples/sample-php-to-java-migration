package com.symfony.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

/**
 * Unit tests for {@link SecurityUser}: authorities are derived from {@link User#getRoles()} with
 * the fully-qualified {@code ROLE_} strings intact, the default {@code ROLE_USER} is honored, and
 * the wrapped domain user / password / username are exposed for downstream use.
 */
class SecurityUserTest {

    private static User user(String username, String password, List<String> roles) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setFullName("Test User");
        u.setPassword(password);
        u.setRoles(roles);
        return u;
    }

    @Test
    void adminRoleMapsToRoleAdminAuthority() {
        SecurityUser securityUser = new SecurityUser(user("jane_admin", "hash", List.of("ROLE_ADMIN")));

        assertThat(securityUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(securityUser.getUsername()).isEqualTo("jane_admin");
        assertThat(securityUser.getPassword()).isEqualTo("hash");
    }

    @Test
    void emptyRolesDefaultToRoleUser() {
        // Mirrors the PHP getRoles() default: a user with no roles still gets ROLE_USER.
        SecurityUser securityUser = new SecurityUser(user("nobody", "hash", List.of()));

        assertThat(securityUser.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void exposesWrappedDomainUser() {
        User domain = user("john_user", "hash", List.of("ROLE_USER"));
        SecurityUser securityUser = new SecurityUser(domain);

        assertThat(securityUser.getDomainUser()).isSameAs(domain);
        assertThat(securityUser.isEnabled()).isTrue();
        assertThat(securityUser.isAccountNonExpired()).isTrue();
        assertThat(securityUser.isAccountNonLocked()).isTrue();
        assertThat(securityUser.isCredentialsNonExpired()).isTrue();
    }
}
