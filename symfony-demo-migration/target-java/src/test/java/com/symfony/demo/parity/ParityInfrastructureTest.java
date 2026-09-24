package com.symfony.demo.parity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that exercises the parity <em>infrastructure</em> itself: it confirms both the PHP
 * Symfony Demo baseline and the Java migration are reachable through the shared
 * {@link ParityHttpClient}s and that {@link ResponseComparison} can diff their blog landing
 * pages.
 *
 * <p>Inherits the environment gate from {@link AbstractParityTest}, so it runs only when both
 * stacks are up (via {@code src/test/resources/parity/compose-parity.yml}). When Docker is
 * unavailable or the compose stack isn't running, this skips cleanly; it becomes an executable
 * end-to-end check the moment the parity environment is running (e.g. in CI). It is the
 * foundation the concrete core-page (17.1) and authenticated/admin (17.2) parity tests build on.
 *
 * <p>The {@code @EnabledIf} gate is declared directly on this concrete class (in addition to the
 * one on {@link AbstractParityTest}) because JUnit does not apply an inherited class-level
 * {@code @EnabledIf} condition to subclasses. Future parity test classes should do the same.
 */
@EnabledIf("parityEnvironmentAvailable")
class ParityInfrastructureTest extends AbstractParityTest {

    @Test
    @DisplayName("both parity targets serve the blog index")
    void bothTargetsReachable() throws Exception {
        ParityResponse phpIndex = php.get("/blog/");
        ParityResponse javaIndex = java.get("/blog/");

        // Both stacks should serve the blog index (200) or redirect to it (3xx).
        assertThat(phpIndex.statusCode()).isBetween(200, 399);
        assertThat(javaIndex.statusCode()).isBetween(200, 399);
    }

    @Test
    @DisplayName("blog index status codes match across targets")
    void blogIndexStatusMatches() throws Exception {
        ParityResponse phpIndex = php.get("/blog/");
        ParityResponse javaIndex = java.get("/blog/");

        ResponseComparison.Result result =
                ResponseComparison.compareStatusCode(phpIndex, javaIndex);
        assertThat(result.matched())
                .as(result.describe())
                .isTrue();
    }
}
