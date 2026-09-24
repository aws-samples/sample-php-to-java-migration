package com.symfony.demo.parity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Compares two {@link ParityResponse}s — one from the PHP Symfony Demo baseline, one
 * from the Java Spring Boot migration — and reports where they diverge.
 *
 * <p>Raw byte-for-byte equality is meaningless here: every Symfony form embeds a fresh
 * anti-CSRF {@code _token}, session identifiers rotate, published/rendered timestamps
 * change per run, and the two stacks differ in incidental markup (Twig vs Thymeleaf
 * whitespace, framework banners). This utility therefore compares at these levels:
 * <ul>
 *   <li><b>Status code</b> — must match exactly.</li>
 *   <li><b>Redirect location</b> — the {@code Location} <em>path</em> is compared
 *       (host/port/query stripped), so a 303 to {@code /blog/posts/foo} matches across
 *       targets bound to different ports.</li>
 *   <li><b>Body structure</b> — JSON bodies are compared by parsed shape (keys + value
 *       types); HTML/text bodies are normalized (CSRF tokens, session ids, timestamps and
 *       whitespace stripped) before comparison.</li>
 * </ul>
 *
 * <p>All comparison methods return a {@link Result} rather than throwing, so a test can
 * aggregate several checks and surface every difference at once.
 */
public final class ResponseComparison {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Symfony anti-CSRF token embedded in forms, e.g.
     * {@code <input type="hidden" name="comment[_token]" value="...">} or
     * {@code name="_csrf_token" value="...">}. Matches any field whose name ends in
     * {@code _token} (optionally bracketed) followed by a value.
     */
    private static final Pattern CSRF_TOKEN = Pattern.compile(
            "name=\"[^\"]*_(?:csrf_)?token\\]?\"\\s+value=\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    /** Session identifiers appearing in cookies/URLs (PHPSESSID / JSESSIONID). */
    private static final Pattern SESSION_ID =
            Pattern.compile("(PHPSESSID|JSESSIONID)=[A-Za-z0-9]+", Pattern.CASE_INSENSITIVE);

    /** RSS/Atom timestamps, e.g. {@code <pubDate>Tue, 01 Jan 2024 ...</pubDate>}. */
    private static final Pattern RSS_DATE =
            Pattern.compile("<(pubDate|lastBuildDate|updated|published)>.*?</\\1>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** ISO-8601-ish date/time stamps embedded in markup, e.g. {@code 2024-01-31T12:34:56}. */
    private static final Pattern ISO_TIMESTAMP =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2})?([.,]\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");

    /** Whitespace between adjacent tags — incidental markup formatting (Twig vs Thymeleaf). */
    private static final Pattern TAG_WHITESPACE = Pattern.compile(">\\s+<");

    /** Collapsible runs of whitespace. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** HTML comments — often carry framework-specific noise. */
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private ResponseComparison() {
    }

    /**
     * Outcome of a comparison: whether the two sides matched, and a human-readable list of
     * every difference discovered.
     *
     * @param matched     true when no differences were found
     * @param differences descriptions of each divergence (empty when {@code matched})
     */
    public record Result(boolean matched, List<String> differences) {

        public static Result ok() {
            return new Result(true, List.of());
        }

        public static Result of(List<String> differences) {
            return new Result(differences.isEmpty(), List.copyOf(differences));
        }

        /** Merges this result with another, unioning their differences. */
        public Result and(Result other) {
            List<String> merged = new ArrayList<>(this.differences);
            merged.addAll(other.differences);
            return Result.of(merged);
        }

        /** A one-line summary suitable for a JUnit assertion message. */
        public String describe() {
            return matched ? "responses match" : String.join("; ", differences);
        }
    }

    // ------------------------------------------------------------------------
    // Status code
    // ------------------------------------------------------------------------

    /** Compares HTTP status codes exactly. */
    public static Result compareStatusCode(ParityResponse php, ParityResponse java) {
        if (php.statusCode() == java.statusCode()) {
            return Result.ok();
        }
        return Result.of(List.of(
                "status code differs: PHP=" + php.statusCode() + " JAVA=" + java.statusCode()));
    }

    // ------------------------------------------------------------------------
    // Redirect location (path-normalized)
    // ------------------------------------------------------------------------

    /**
     * Compares the redirect target of the two responses by {@code Location} <em>path</em>
     * (host, port and query string stripped), so a redirect to {@code /blog/} matches even
     * when the two stacks are bound to different ports. When neither response is a redirect
     * this is a no-op match; when only one redirects, that is reported.
     */
    public static Result compareRedirectLocation(ParityResponse php, ParityResponse java) {
        boolean phpRedirect = php.isRedirect();
        boolean javaRedirect = java.isRedirect();
        if (!phpRedirect && !javaRedirect) {
            return Result.ok();
        }
        if (phpRedirect != javaRedirect) {
            return Result.of(List.of(
                    "redirect presence differs: PHP redirect=" + phpRedirect
                            + " JAVA redirect=" + javaRedirect));
        }
        String phpPath = php.locationPath().orElse("");
        String javaPath = java.locationPath().orElse("");
        if (phpPath.equals(javaPath)) {
            return Result.ok();
        }
        return Result.of(List.of(
                "redirect location path differs: PHP=" + phpPath + " JAVA=" + javaPath));
    }

    // ------------------------------------------------------------------------
    // Cookies / headers
    // ------------------------------------------------------------------------

    /**
     * Compares the <em>names</em> of cookies each response sets (values are session-specific
     * and intentionally ignored).
     */
    public static Result compareCookieNames(ParityResponse php, ParityResponse java) {
        var phpNames = php.cookieNames();
        var javaNames = java.cookieNames();
        if (phpNames.equals(javaNames)) {
            return Result.ok();
        }
        return Result.of(List.of(
                "Set-Cookie names differ: PHP=" + phpNames + " JAVA=" + javaNames));
    }

    /** Compares a single header value (case-insensitive header name) exactly. */
    public static Result compareHeader(String headerName, ParityResponse php, ParityResponse java) {
        String phpValue = php.header(headerName).orElse(null);
        String javaValue = java.header(headerName).orElse(null);
        if (Objects.equals(phpValue, javaValue)) {
            return Result.ok();
        }
        return Result.of(List.of(
                headerName + " header differs: PHP=" + phpValue + " JAVA=" + javaValue));
    }

    // ------------------------------------------------------------------------
    // Body structure (JSON-aware / normalized HTML)
    // ------------------------------------------------------------------------

    /**
     * Compares response body <em>structure</em>. When both responses are JSON, their parsed
     * shapes are compared (object keys and value types, order-independent). Otherwise the
     * bodies are {@link #normalize(String) normalized} and compared as text.
     */
    public static Result compareBodyStructure(ParityResponse php, ParityResponse java) {
        if (php.isJson() && java.isJson()) {
            return compareJsonStructure(php.body(), java.body());
        }
        String normalizedPhp = normalize(php.body());
        String normalizedJava = normalize(java.body());
        if (normalizedPhp.equals(normalizedJava)) {
            return Result.ok();
        }
        return Result.of(List.of(
                "normalized body structure differs (php " + normalizedPhp.length()
                        + " chars vs java " + normalizedJava.length() + " chars)"));
    }

    /**
     * JSON-aware structural comparison: parses both bodies and walks them, requiring the same
     * object keys and the same value <em>types</em> at each position. Scalar values are not
     * required to be equal (they may legitimately differ), only their JSON type.
     */
    public static Result compareJsonStructure(String phpBody, String javaBody) {
        JsonNode phpNode;
        JsonNode javaNode;
        try {
            phpNode = MAPPER.readTree(phpBody);
        } catch (Exception e) {
            return Result.of(List.of("PHP body is not valid JSON: " + e.getMessage()));
        }
        try {
            javaNode = MAPPER.readTree(javaBody);
        } catch (Exception e) {
            return Result.of(List.of("JAVA body is not valid JSON: " + e.getMessage()));
        }
        List<String> differences = new ArrayList<>();
        walkJson("$", phpNode, javaNode, differences);
        return Result.of(differences);
    }

    private static void walkJson(String path, JsonNode php, JsonNode java, List<String> diffs) {
        if (php.getNodeType() != java.getNodeType()) {
            diffs.add("JSON type differs at " + path + ": PHP=" + php.getNodeType()
                    + " JAVA=" + java.getNodeType());
            return;
        }
        if (php.isObject()) {
            var phpFields = new java.util.TreeSet<String>();
            php.fieldNames().forEachRemaining(phpFields::add);
            var javaFields = new java.util.TreeSet<String>();
            java.fieldNames().forEachRemaining(javaFields::add);
            if (!phpFields.equals(javaFields)) {
                diffs.add("JSON object keys differ at " + path + ": PHP=" + phpFields
                        + " JAVA=" + javaFields);
                return;
            }
            for (String field : phpFields) {
                walkJson(path + "." + field, php.get(field), java.get(field), diffs);
            }
        } else if (php.isArray()) {
            if (php.size() != java.size()) {
                diffs.add("JSON array length differs at " + path + ": PHP=" + php.size()
                        + " JAVA=" + java.size());
                return;
            }
            for (int i = 0; i < php.size(); i++) {
                walkJson(path + "[" + i + "]", php.get(i), java.get(i), diffs);
            }
        }
        // Scalars: matching node type is sufficient for structural parity.
    }

    /**
     * Runs the full default parity check (status code + redirect location + body structure)
     * and returns the combined result. Callers can layer additional {@code compare*} checks on
     * top.
     */
    public static Result compareAll(ParityResponse php, ParityResponse java) {
        return compareStatusCode(php, java)
                .and(compareRedirectLocation(php, java))
                .and(compareBodyStructure(php, java));
    }

    // ------------------------------------------------------------------------
    // Normalization helpers
    // ------------------------------------------------------------------------

    /**
     * Normalizes an HTML/text body for stable cross-stack comparison by removing values that
     * legitimately differ on every request or between frameworks:
     * <ul>
     *   <li>Symfony anti-CSRF {@code _token} field values</li>
     *   <li>session identifiers ({@code PHPSESSID}/{@code JSESSIONID}) — canonicalized to a
     *       single token so the two stacks' differently-named session cookies compare equal</li>
     *   <li>RSS/Atom timestamps and ISO-8601 date/time stamps</li>
     *   <li>HTML comments</li>
     *   <li>whitespace between adjacent tags (Twig compact markup vs Thymeleaf indentation)</li>
     *   <li>collapsible whitespace</li>
     * </ul>
     * The result is trimmed and lower-cased for case-insensitive structural comparison.
     */
    public static String normalize(String body) {
        if (body == null) {
            return "";
        }
        String result = body;
        result = CSRF_TOKEN.matcher(result).replaceAll("name=\"_token\" value=\"TOKEN\"");
        result = SESSION_ID.matcher(result).replaceAll("SESSIONID=SESSION");
        result = RSS_DATE.matcher(result).replaceAll("<date>TIMESTAMP</date>");
        result = ISO_TIMESTAMP.matcher(result).replaceAll("TIMESTAMP");
        result = HTML_COMMENT.matcher(result).replaceAll("");
        result = TAG_WHITESPACE.matcher(result).replaceAll("><");
        result = WHITESPACE.matcher(result).replaceAll(" ");
        return result.trim().toLowerCase();
    }
}
