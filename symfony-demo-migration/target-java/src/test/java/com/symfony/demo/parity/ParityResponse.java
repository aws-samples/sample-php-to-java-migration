package com.symfony.demo.parity;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable snapshot of an HTTP response captured from one of the parity targets
 * (the PHP Symfony Demo baseline or the Java Spring Boot migration).
 *
 * <p>Captures just the pieces the parity comparison cares about — status code, the
 * response body as text, and the response headers (including {@code Location} and
 * {@code Set-Cookie}) — so a {@link ResponseComparison} can diff two responses
 * without re-issuing requests.
 *
 * @param target     which side produced this response (PHP baseline or Java migration)
 * @param requestUri the absolute URI that was requested
 * @param statusCode the HTTP status code
 * @param body       the response body decoded as UTF-8 text
 * @param headers    the raw response headers
 */
public record ParityResponse(
        Target target,
        String requestUri,
        int statusCode,
        String body,
        HttpHeaders headers) {

    /** Identifies which implementation a response (or request) belongs to. */
    public enum Target {
        /** The original PHP Symfony Demo baseline. */
        PHP,
        /** The migrated Java/Spring Boot application. */
        JAVA
    }

    /** Returns the first value of {@code name}, if present (case-insensitive). */
    public Optional<String> header(String name) {
        return headers.firstValue(name);
    }

    /** Returns the {@code Content-Type} header value, or empty string if absent. */
    public String contentType() {
        return headers.firstValue("Content-Type").orElse("");
    }

    /** True when the response advertises a JSON content type. */
    public boolean isJson() {
        return contentType().toLowerCase().contains("json");
    }

    /** True when the status code is a redirect (3xx) carrying a {@code Location}. */
    public boolean isRedirect() {
        return statusCode >= 300 && statusCode < 400 && header("Location").isPresent();
    }

    /** The raw {@code Location} header value, if present. */
    public Optional<String> location() {
        return header("Location");
    }

    /**
     * The path portion of the {@code Location} header (scheme/host/query stripped),
     * so redirect targets can be compared across targets that differ only in host/port
     * (e.g. {@code http://localhost:8000/blog/} vs {@code http://localhost:8080/blog/}).
     */
    public Optional<String> locationPath() {
        return location().map(ParityResponse::pathOf);
    }

    private static String pathOf(String location) {
        try {
            URI uri = URI.create(location);
            String path = uri.getPath();
            return path == null || path.isEmpty() ? location : path;
        } catch (RuntimeException e) {
            // Relative or malformed Location — fall back to a best-effort path extraction.
            int q = location.indexOf('?');
            return q >= 0 ? location.substring(0, q) : location;
        }
    }

    /** All raw {@code Set-Cookie} header values (may be empty). */
    public List<String> setCookies() {
        return headers.allValues("Set-Cookie");
    }

    /**
     * Parses the {@code Set-Cookie} headers into a name-&gt;value map (attributes such as
     * {@code Path}/{@code HttpOnly} are dropped). Useful for comparing cookie <em>names</em>
     * across targets without being sensitive to per-session cookie <em>values</em>.
     */
    public Map<String, String> cookies() {
        Map<String, String> cookies = new TreeMap<>();
        for (String setCookie : setCookies()) {
            String first = setCookie.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq > 0) {
                cookies.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
            }
        }
        return cookies;
    }

    /** Just the cookie names set by this response, order-independent. */
    public Set<String> cookieNames() {
        return cookies().keySet();
    }
}
