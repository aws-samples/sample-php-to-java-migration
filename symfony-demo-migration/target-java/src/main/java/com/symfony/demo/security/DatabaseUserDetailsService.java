package com.symfony.demo.security;

import com.symfony.demo.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * {@link UserDetailsService} backed by {@link UserRepository}, loading users by username.
 *
 * <p>Port of the Symfony security provider configured in {@code config/packages/security.yaml}:
 * <pre>
 * providers:
 *   database_users:
 *     entity: { class: App\Entity\User, property: username }
 * </pre>
 * Symfony loads the user entity by its {@code username} property; this service mirrors that by
 * calling {@link UserRepository#findByUsername(String)} and wrapping the result in a
 * {@link SecurityUser}. A missing user raises {@link UsernameNotFoundException}, which Spring
 * Security reports as bad credentials — matching the PHP app's opaque authentication failure.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(SecurityUser::new)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User \"%s\" not found.".formatted(username)));
    }
}
