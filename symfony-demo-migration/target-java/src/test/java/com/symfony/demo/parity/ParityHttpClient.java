package com.symfony.demo.parity;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A thin HTTP client that issues requests against a single parity
 * {@link ParityResponse.Target}. A parity test typically holds two of these — one
 * pointed at the PHP Symfony Demo baseline and one at the Java migration — and submits
 * identical inputs to both, then diffs the {@link ParityResponse}s with
 * {@link ResponseComparison}.
 *
 * <p>Each instance keeps its own {@link CookieManager} so a login/session established
 * on one target is preserved across subsequent requests to that same target, without
 * bleeding cookies between PHP and Java.
 *
 * <p>Redirects are intentionally <b>not</b> followed automatically: parity depends on
 * observing redirect status codes and {@code Location} headers (e.g. a 303 back to a
 * post page after commenting, or {@code /login} redirects), which disappear if the
 * client chases the redirect transparently.
 */
public final class ParityHttpClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ParityResponse.Target target;
    private final String baseUrl;
    private final HttpClient httpClient;

    /**
     * @param target  which implementation this client talks to (for labelling responses)
     * @param baseUrl the scheme://host:port root, e.g. {@code http://localhost:8000}
     */
    public ParityHttpClient(ParityResponse.Target target, String baseUrl) {
        this.target = target;
        this.baseUrl = stripTrailingSlash(baseUrl);
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager)
                .connectTimeout(TIMEOUT)
                .build();
    }

    /** The base URL this client targets (no trailing slash). */
    public String baseUrl() {
        return baseUrl;
    }

    /** The target implementation this client talks to. */
    public ParityResponse.Target target() {
        return target;
    }

    /**
     * Issues a GET to {@code path} (e.g. {@code "/blog/posts/hello-world"}). A path that
     * already starts with {@code http} is treated as absolute; otherwise it is resolved
     * against the base URL.
     */
    public ParityResponse get(String path) throws IOException, InterruptedException {
        URI uri = resolve(path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .GET()
                .build();
        return send(uri, request);
    }

    /**
     * Issues a form-encoded ({@code application/x-www-form-urlencoded}) POST to {@code path}
     * with the supplied field map. Field iteration order is preserved for stable,
     * comparable request bodies.
     */
    public ParityResponse postForm(String path, Map<String, String> formFields)
            throws IOException, InterruptedException {
        URI uri = resolve(path);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(urlEncode(formFields)))
                .build();
        return send(uri, request);
    }

    private ParityResponse send(URI uri, HttpRequest request)
            throws IOException, InterruptedException {
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new ParityResponse(
                target, uri.toString(), response.statusCode(), response.body(), response.headers());
    }

    private URI resolve(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return URI.create(path);
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return URI.create(baseUrl + normalized);
    }

    private static String urlEncode(Map<String, String> fields) {
        // Preserve insertion order for stable, comparable request bodies.
        Map<String, String> ordered =
                fields instanceof LinkedHashMap ? fields : new LinkedHashMap<>(fields);
        return ordered.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
