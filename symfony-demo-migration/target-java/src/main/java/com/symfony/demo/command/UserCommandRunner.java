package com.symfony.demo.command;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Dispatches the user-management console commands ported from Symfony
 * ({@code app:add-user}, {@code app:delete-user}, {@code app:list-users} / {@code app:users}).
 *
 * <p>This runs as a Spring {@link ApplicationRunner}. It inspects the first CLI argument: if it is
 * not one of the recognized command names it returns immediately, so normal web startup
 * ({@code spring-boot:run} with no args) and the test suite are unaffected. When a recognized
 * command is present, the corresponding command executes and the application then exits with the
 * command's exit code — matching the one-shot nature of a Symfony console command (the embedded
 * web server does not stay up).
 */
@Component
public class UserCommandRunner implements ApplicationRunner {

    private static final String ADD_USER = "app:add-user";
    private static final String DELETE_USER = "app:delete-user";
    private static final String LIST_USERS = "app:list-users";
    private static final String LIST_USERS_ALIAS = "app:users";

    private final ConfigurableApplicationContext context;
    private final AddUserCommand addUserCommand;
    private final DeleteUserCommand deleteUserCommand;
    private final ListUsersCommand listUsersCommand;

    public UserCommandRunner(ConfigurableApplicationContext context,
                             AddUserCommand addUserCommand,
                             DeleteUserCommand deleteUserCommand,
                             ListUsersCommand listUsersCommand) {
        this.context = context;
        this.addUserCommand = addUserCommand;
        this.deleteUserCommand = deleteUserCommand;
        this.listUsersCommand = listUsersCommand;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] source = args.getSourceArgs();
        if (source.length == 0) {
            return;
        }

        String command = source[0];
        if (!isRecognized(command)) {
            // Not a console command invocation — let the web application start normally.
            return;
        }

        List<String> commandArgs = Arrays.asList(source).subList(1, source.length);

        int exitCode = switch (command) {
            case ADD_USER -> addUserCommand.run(commandArgs, System.out, System.err);
            case DELETE_USER -> deleteUserCommand.run(commandArgs, System.out, System.err);
            case LIST_USERS, LIST_USERS_ALIAS -> listUsersCommand.run(commandArgs, System.out, System.err);
            default -> 0;
        };

        // A console command is a one-shot process: shut the context down and exit so the embedded
        // web server does not linger after the command completes.
        int code = SpringApplication.exit(context, () -> exitCode);
        System.exit(code);
    }

    private static boolean isRecognized(String command) {
        return ADD_USER.equals(command)
                || DELETE_USER.equals(command)
                || LIST_USERS.equals(command)
                || LIST_USERS_ALIAS.equals(command);
    }
}
