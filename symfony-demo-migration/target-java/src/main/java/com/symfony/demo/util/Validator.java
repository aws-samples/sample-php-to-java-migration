package com.symfony.demo.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Input validation helper, port of the PHP {@code App\Utils\Validator}.
 *
 * <p>The rules and error messages are reproduced verbatim from the PHP source so the console
 * commands (task 13.1) behave identically. Symfony threw
 * {@code Symfony\Component\Console\Exception\InvalidArgumentException}; the Java port throws
 * {@link IllegalArgumentException} (its closest standard-library equivalent) with the same
 * messages.
 */
@Component
public class Validator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z_]+$");

    /**
     * Validates a username: non-empty and only lowercase latin characters and underscores.
     */
    public String validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("The username can not be empty.");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "The username must contain only lowercase latin characters and underscores.");
        }
        return username;
    }

    /**
     * Validates a plain password: non-empty and at least 6 characters after trimming.
     */
    public String validatePassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("The password can not be empty.");
        }
        if (plainPassword.trim().length() < 6) {
            throw new IllegalArgumentException("The password must be at least 6 characters long.");
        }
        return plainPassword;
    }

    /**
     * Validates an email: non-empty and containing an {@code @} sign (matches the PHP
     * {@code indexOf('@')} check — deliberately lenient, not a full RFC email check).
     */
    public String validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("The email can not be empty.");
        }
        if (email.indexOf('@') < 0) {
            throw new IllegalArgumentException("The email should look like a real email.");
        }
        return email;
    }

    /**
     * Validates a full name: non-empty.
     */
    public String validateFullName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            throw new IllegalArgumentException("The full name can not be empty.");
        }
        return fullName;
    }
}
