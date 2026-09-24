package com.symfony.demo.security;

import com.symfony.demo.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security {@link UserDetails} adapter wrapping the domain {@link User} entity.
 *
 * <p>Port of Symfony's use of the {@code App\Entity\User} as its {@code UserInterface}. Keeping a
 * reference to the wrapped domain {@link User} (via {@link #getDomainUser()}) lets downstream
 * components resolve the authenticated entity through {@code @AuthenticationPrincipal} — notably
 * the {@code PostVoter} parity check (task 6.2), which compares the current user against a post's
 * author.
 *
 * <p>Authorities are derived from {@link User#getRoles()}, which already returns fully-qualified
 * Symfony role strings (e.g. {@code ROLE_ADMIN}, {@code ROLE_USER}) and guarantees the default
 * {@code ROLE_USER}. Because the roles are already {@code ROLE_}-prefixed, they map directly to
 * {@link SimpleGrantedAuthority} without additional prefixing. (Spring's {@code hasRole('ADMIN')}
 * checks for the authority {@code ROLE_ADMIN}, i.e. it re-adds the prefix it strips — so these
 * stored strings line up with {@code hasRole}/{@code hasAuthority} semantics.)
 */
public class SecurityUser implements UserDetails {

    private final User user;
    private final List<GrantedAuthority> authorities;

    public SecurityUser(User user) {
        this.user = user;
        this.authorities = user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /** Returns the wrapped domain entity (used by authorization checks and controllers). */
    public User getDomainUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
