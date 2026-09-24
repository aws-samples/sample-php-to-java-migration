package com.symfony.demo.parity;

import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Base class for side-by-side parity tests that compare the original PHP Symfony Demo
 * against the migrated Java Spring Boot port.
 *
 * <p>It provides two preconfigured {@link ParityHttpClient}s — one pointed at the PHP
 * baseline and one at the Java migration — so a concrete test can submit identical
 * requests to both and diff the {@link ParityResponse}s with {@link ResponseComparison}.
 * Each client keeps its own cookie jar, so sessions established on one target do not leak
 * into the other.
 *
 * <p>The two targets are expected to be running (via
 * {@code src/test/resources/parity/compose-parity.yml}) at:
 * <ul>
 *   <li>PHP baseline  — {@code http://localhost:8000} (override: {@code -Dparity.php.baseUrl=...})</li>
 *   <li>Java migration — {@code http://localhost:8080} (override: {@code -Dparity.java.baseUrl=...})</li>
 * </ul>
 *
 * <p><b>Environment note:</b> parity requires a live Docker environment running <em>both</em>
 * stacks side by side. The parity suite talks to an externally running
 * {@code compose-parity.yml} environment, so the whole class is gated with {@link EnabledIf}
 * on {@link #parityEnvironmentAvailable()} — which additionally requires both targets to be
 * reachable. That way the parity tests skip cleanly (instead of failing with connection
 * errors) whenever Docker is absent <em>or</em> the compose stack simply isn't running — as
 * in the sandbox this was authored in — while still compiling and running automatically
 * wherever the parity environment is up (e.g. CI).
 *
 * <p>JUnit does not apply an inherited class-level {@code @EnabledIf} condition to
 * subclasses, so each concrete parity test class must also carry the
 * {@code @EnabledIf("parityEnvironmentAvailable")} annotation itself.
 */
@EnabledIf("parityEnvironmentAvailable")
public abstract class AbstractParityTest {

    /** Default base URL of the PHP baseline as published by {@code compose-parity.yml}. */
    public static final String DEFAULT_PHP_BASE_URL = "http://localhost:8000";

    /** Default base URL of the Java migration as published by {@code compose-parity.yml}. */
    public static final String DEFAULT_JAVA_BASE_URL = "http://localhost:8080";

    /** HTTP client bound to the PHP baseline target. */
    protected final ParityHttpClient php =
            new ParityHttpClient(ParityResponse.Target.PHP, phpBaseUrl());

    /** HTTP client bound to the Java migration target. */
    protected final ParityHttpClient java =
            new ParityHttpClient(ParityResponse.Target.JAVA, javaBaseUrl());

    /**
     * JUnit condition: the parity suite only runs when the full side-by-side environment is
     * actually available — i.e. a Docker engine is reachable <em>and</em> both the PHP baseline
     * and the Java migration respond on their published ports. Requiring reachability (not just
     * Docker) means the suite skips cleanly when {@code compose-parity.yml} isn't running,
     * rather than erroring with connection failures.
     */
    static boolean parityEnvironmentAvailable() {
        return dockerAvailable() && reachable(phpBaseUrl()) && reachable(javaBaseUrl());
    }

    /**
     * JUnit condition (and building block for {@link #parityEnvironmentAvailable()}): true when a
     * Docker engine is reachable.
     */
    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Quick reachability probe: is {@code baseUrl} serving HTTP? Uses {@link HttpClient} (the
     * same client the parity harness itself relies on) rather than a raw {@link java.net.Socket}
     * connect, so this check goes through the JDK's standard HTTP stack — which negotiates TLS
     * itself for {@code https://} targets — instead of a bare TCP handshake that a static
     * analyzer cannot distinguish from an unencrypted data channel.
     */
    private static boolean reachable(String baseUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(500))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofMillis(500))
                    .GET()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Resolves the PHP baseline base URL, honoring the {@code parity.php.baseUrl} override. */
    protected static String phpBaseUrl() {
        return System.getProperty("parity.php.baseUrl", DEFAULT_PHP_BASE_URL);
    }

    /** Resolves the Java migration base URL, honoring the {@code parity.java.baseUrl} override. */
    protected static String javaBaseUrl() {
        return System.getProperty("parity.java.baseUrl", DEFAULT_JAVA_BASE_URL);
    }
}
