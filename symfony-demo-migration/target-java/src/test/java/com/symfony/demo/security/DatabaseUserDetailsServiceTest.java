package com.symfony.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.symfony.demo.entity.User;
import com.symfony.demo.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Unit tests for {@link DatabaseUserDetailsService}, exercising the found / not-found branches and
 * verifying the real {@link SecurityUser} mapping (username, password, authorities, wrapped entity).
 * The {@link UserRepository} boundary is stubbed so the test targets the service's own logic.
 */
class DatabaseUserDetailsServiceTest {

    private static User user(String username, List<String> roles) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setFullName("Demo User");
        u.setPassword("$2y$13$hash");
        u.setRoles(roles);
        return u;
    }

    @Test
    void loadsExistingUserByUsernameWithAuthorities() {
        User jane = user("jane_admin", List.of("ROLE_ADMIN"));
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByUsername("jane_admin")).thenReturn(Optional.of(jane));
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(repository);

        UserDetails details = service.loadUserByUsername("jane_admin");

        assertThat(details).isInstanceOf(SecurityUser.class);
        assertThat(details.getUsername()).isEqualTo("jane_admin");
        assertThat(details.getPassword()).isEqualTo("$2y$13$hash");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(((SecurityUser) details).getDomainUser()).isSameAs(jane);
    }

    @Test
    void throwsWhenUsernameNotFound() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());
        DatabaseUserDetailsService service = new DatabaseUserDetailsService(repository);

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }
}
