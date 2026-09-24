package com.symfony.demo.repository;

import com.symfony.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User}. Port of the PHP {@code App\Repository\UserRepository}.
 * The PHP repository declared magic finders {@code findOneByUsername}/{@code findOneByEmail};
 * those are expressed here as explicit derived-query methods used by the security layer
 * (UserDetailsService, task 6.1) and console commands.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Finds a user by their unique username (equivalent to PHP {@code findOneByUsername}).
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their unique email (equivalent to PHP {@code findOneByEmail}).
     */
    Optional<User> findByEmail(String email);
}
