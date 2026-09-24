package com.symfony.demo.command;

import com.symfony.demo.entity.User;
import com.symfony.demo.repository.UserRepository;
import com.symfony.demo.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/**
 * Console command that deletes a user from the database. Port of the PHP
 * {@code App\Command\DeleteUserCommand} ({@code app:delete-user}).
 *
 * <p>Argument (positional): {@code username}. The username is validated (same rules as the PHP
 * command), then looked up; a missing user raises an error. On success the removed user's id,
 * username, and email are reported and logged. The PHP interactive wizard is not ported (the
 * argument is required) — noted in BEHAVIOR_CHANGES.md.
 */
@Component
public class DeleteUserCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteUserCommand.class);

    private final UserRepository users;
    private final Validator validator;

    public DeleteUserCommand(UserRepository users, Validator validator) {
        this.users = users;
        this.validator = validator;
    }

    /**
     * Runs {@code app:delete-user}. Returns 0 on success, 1 on a validation/usage/not-found error.
     */
    public int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.isEmpty()) {
            err.println("[ERROR] Not enough arguments. Usage: app:delete-user <username>");
            return 1;
        }

        try {
            String username = validator.validateUsername(args.get(0));

            Optional<User> found = users.findByUsername(username);
            if (found.isEmpty()) {
                throw new IllegalArgumentException("User with username \"" + username + "\" not found.");
            }

            User user = found.get();
            Integer userId = user.getId();
            String userUsername = user.getUsername();
            String userEmail = user.getEmail();

            users.delete(user);
            users.flush();

            out.printf("[OK] User \"%s\" (ID: %d, email: %s) was successfully deleted.%n",
                    userUsername, userId, userEmail);
            LOGGER.info("User \"{}\" (ID: {}, email: {}) was successfully deleted.",
                    userUsername, userId, userEmail);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("[ERROR] " + e.getMessage());
            return 1;
        }
    }
}
