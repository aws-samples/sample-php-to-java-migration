package com.symfony.demo.command;

import com.symfony.demo.entity.User;
import com.symfony.demo.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.util.List;

/**
 * Console command that lists the existing users. Port of the PHP
 * {@code App\Command\ListUsersCommand} ({@code app:list-users}, alias {@code app:users}).
 *
 * <p>Option: {@code --max-results=N} (also accepts {@code --max-results N}); defaults to 50. Users
 * are ordered by id descending and rendered as a table with columns ID, Full Name, Username,
 * Email, Roles (roles joined with ", ", matching PHP {@code implode(', ', $user->getRoles())}).
 *
 * <p>The PHP {@code --send-to} email-report feature is intentionally omitted — noted in
 * BEHAVIOR_CHANGES.md.
 */
@Component
public class ListUsersCommand {

    private static final int DEFAULT_MAX_RESULTS = 50;

    private static final String[] HEADERS = {"ID", "Full Name", "Username", "Email", "Roles"};

    private final UserRepository users;

    public ListUsersCommand(UserRepository users) {
        this.users = users;
    }

    /**
     * Runs {@code app:list-users}. Returns 0 on success, 1 on a usage error.
     */
    public int run(List<String> args, PrintStream out, PrintStream err) {
        int maxResults = DEFAULT_MAX_RESULTS;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            String value = null;
            if (arg.startsWith("--max-results=")) {
                value = arg.substring("--max-results=".length());
            } else if ("--max-results".equals(arg) && i + 1 < args.size()) {
                value = args.get(++i);
            } else {
                continue;
            }
            try {
                maxResults = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                err.println("[ERROR] --max-results must be an integer.");
                return 1;
            }
        }

        if (maxResults < 1) {
            err.println("[ERROR] --max-results must be a positive integer.");
            return 1;
        }

        List<User> allUsers = users.findAll(
                PageRequest.of(0, maxResults, Sort.by(Sort.Direction.DESC, "id"))).getContent();

        List<String[]> rows = allUsers.stream()
                .map(user -> new String[]{
                        String.valueOf(user.getId()),
                        nullToEmpty(user.getFullName()),
                        nullToEmpty(user.getUsername()),
                        nullToEmpty(user.getEmail()),
                        String.join(", ", user.getRoles()),
                })
                .toList();

        printTable(out, rows);
        return 0;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void printTable(PrintStream out, List<String[]> rows) {
        int[] widths = new int[HEADERS.length];
        for (int c = 0; c < HEADERS.length; c++) {
            widths[c] = HEADERS[c].length();
        }
        for (String[] row : rows) {
            for (int c = 0; c < HEADERS.length; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }

        String separator = buildSeparator(widths);
        out.println(separator);
        out.println(formatRow(HEADERS, widths));
        out.println(separator);
        for (String[] row : rows) {
            out.println(formatRow(row, widths));
        }
        out.println(separator);
    }

    private static String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : widths) {
            sb.append("-".repeat(width + 2)).append("+");
        }
        return sb.toString();
    }

    private static String formatRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < widths.length; c++) {
            sb.append(' ').append(padRight(cells[c], widths[c])).append(" |");
        }
        return sb.toString();
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
