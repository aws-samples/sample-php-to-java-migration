package com.symfony.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Login-parity test for Requirement 7.5: the seeded {@code $2y$} bcrypt hashes of the demo
 * password "kitten" must verify with Spring's {@link BCryptPasswordEncoder}, so jane_admin /
 * tom_admin / john_user authenticate identically to the PHP app with no re-hashing.
 *
 * <p>The hashes are read directly from {@code V2__seed.sql} at test time rather than duplicated
 * as literals here, so this test always exercises whatever is actually seeded (single source of
 * truth) and so no bcrypt-hash-shaped string literals live in Java source, where secret scanners
 * (correctly, in general) treat that pattern as a potential hardcoded credential.
 */
class PasswordEncoderParityTest {

    private static final Path SEED_SQL =
            Path.of("src/main/resources/db/migration/V2__seed.sql");

    // Matches: (id, 'Full Name', 'username', 'email', '<bcrypt-hash>', '[...]')
    private static final Pattern USER_ROW =
            Pattern.compile("'(\\w+)',\\s*'[^']*@[^']*',\\s*'(\\$2[aby]\\$[^']+)'");

    private static Map<String, String> hashesByUsername;

    @BeforeAll
    static void loadSeededHashes() {
        String sql;
        try {
            sql = Files.readString(SEED_SQL);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + SEED_SQL.toAbsolutePath(), e);
        }

        hashesByUsername = new HashMap<>();
        Matcher matcher = USER_ROW.matcher(sql);
        while (matcher.find()) {
            hashesByUsername.put(matcher.group(1), matcher.group(2));
        }
        assertThat(hashesByUsername)
                .as("expected jane_admin/tom_admin/john_user rows in " + SEED_SQL)
                .containsKeys("jane_admin", "tom_admin", "john_user");
    }

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void seededHashesVerifyTheDemoPassword() {
        assertThat(encoder.matches("kitten", hashesByUsername.get("jane_admin"))).isTrue();
        assertThat(encoder.matches("kitten", hashesByUsername.get("tom_admin"))).isTrue();
        assertThat(encoder.matches("kitten", hashesByUsername.get("john_user"))).isTrue();
    }

    @Test
    void wrongPasswordIsRejectedForSeededHashes() {
        assertThat(encoder.matches("wrong", hashesByUsername.get("jane_admin"))).isFalse();
        assertThat(encoder.matches("Kitten", hashesByUsername.get("tom_admin"))).isFalse();
        assertThat(encoder.matches("", hashesByUsername.get("john_user"))).isFalse();
    }

    @Test
    void freshlyEncodedPasswordRoundTrips() {
        // Change-password (task 10.2) re-encodes with the same encoder; ensure round-trip works
        // and produces a bcrypt hash compatible with the stored format.
        String encoded = encoder.encode("kitten");
        assertThat(encoded).startsWith("$2");
        assertThat(encoder.matches("kitten", encoded)).isTrue();
    }
}
