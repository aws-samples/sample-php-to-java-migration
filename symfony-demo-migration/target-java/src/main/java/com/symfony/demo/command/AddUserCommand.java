package com.symfony.demo.command;

import com.symfony.demo.entity.User;
import com.symfony.demo.repository.UserRepository;
import com.symfony.demo.util.Validator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Console command that creates a user and stores it in the database. Port of the PHP
 * {@code App\Command\AddUserCommand} ({@code app:add-user}).
 *
 * <p>Arguments (positional): {@code username password email full-name}. Flag: {@code --admin}
 * creates the user with {@link User#ROLE_ADMIN} instead of {@link User#ROLE_USER}.
 *
 * <p>The PHP command also offered an interactive wizard to prompt for missing arguments; the Java
 * port is non-interactive (all four arguments are required) — noted in BEHAVIOR_CHANGES.md.
 * Validation mirrors the PHP {@code validateUserData()}: username/email uniqueness plus
 * password/email/full-name rules from {@link Validator} (the PHP arguments path does not
 * regex-validate the username, so neither does this port).
 */
@Component
public class AddUserCommand {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AddUserCommand(UserRepository users, PasswordEncoder passwordEncoder, Validator validator) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    /**
     * Runs {@code app:add-user}. Returns 0 on success, 1 on a validation/usage error.
     */
    public int run(List<String> args, PrintStream out, PrintStream err) {
        boolean isAdmin = false;
        List<String> positionals = new ArrayList<>();
        for (String arg : args) {
            if ("--admin".equals(arg)) {
                isAdmin = true;
            } else {
                positionals.add(arg);
            }
        }

        if (positionals.size() < 4) {
            err.println("[ERROR] Not enough arguments. Usage: app:add-user "
                    + "<username> <password> <email> <full-name> [--admin]");
            return 1;
        }

        String username = positionals.get(0);
        String plainPassword = positionals.get(1);
        String email = positionals.get(2);
        String fullName = positionals.get(3);

        try {
            validateUserData(username, plainPassword, email, fullName);

            User user = new User();
            user.setFullName(fullName);
            user.setUsername(username);
            user.setEmail(email);
            user.setRoles(List.of(isAdmin ? User.ROLE_ADMIN : User.ROLE_USER));
            user.setPassword(passwordEncoder.encode(plainPassword));

            users.saveAndFlush(user);

            out.printf("[OK] %s was successfully created: %s (%s)%n",
                    isAdmin ? "Administrator user" : "User", user.getUsername(), user.getEmail());
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("[ERROR] " + e.getMessage());
            return 1;
        }
    }

    private void validateUserData(String username, String plainPassword, String email, String fullName) {
        // Mirror PHP validateUserData(): username uniqueness first, then value validation,
        // then email uniqueness.
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                    "There is already a user registered with the \"" + username + "\" username.");
        }

        validator.validatePassword(plainPassword);
        validator.validateEmail(email);
        validator.validateFullName(fullName);

        if (users.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                    "There is already a user registered with the \"" + email + "\" email.");
        }
    }
}
