package com.symfony.demo.parity;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 gate: <b>validate the judge before trusting its verdict.</b>
 *
 * <p>The parity harness ({@link ResponseComparison#compareAll}) is the objective referee for the
 * PHP&nbsp;&rarr;&nbsp;Java migration. Before it can be used to decide whether the port is correct,
 * it must itself be proven trustworthy: it has to <em>PASS</em> when two responses are equivalent
 * (differing only in legitimate per-request noise) and <em>FAIL</em> when they diverge in a way
 * that matters. A judge that always passes, or that trips over CSRF tokens, is worse than no judge.
 *
 * <p>These tests drive the <b>aggregate</b> judge — the exact {@code compareAll} method the parity
 * suite uses — through every breakage class the harness is supposed to catch:
 * <ol>
 *   <li>flipped status code,</li>
 *   <li>changed redirect {@code Location} path,</li>
 *   <li>JSON structural divergence (key rename / value-type change),</li>
 *   <li>materially different (normalized) HTML body,</li>
 * </ol>
 * and then confirms the judge stays quiet for the legitimate-noise cases (CSRF {@code _token},
 * session id, timestamps, inter-tag whitespace). Together these prove the judge catches real
 * breakage without crying wolf over incidental cross-stack differences.
 *
 * <p>Pure JVM tests — no Docker, no live stack — so this gate always runs (locally and in CI) and
 * guards the comparison logic the environment-gated parity tests depend on. The complementary
 * <em>live-stack</em> validation (harness passes against unmodified PHP, then fails once PHP is
 * deliberately broken) is Docker-gated and documented in
 * {@code src/test/resources/parity/PARITY_VALIDATION.md}.
 *
 * <p>Validates: Requirements 17.3
 */
@DisplayName("Judge validation (Phase 1 gate) — trustworthy referee")
class JudgeValidationTest {

    // ------------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------------

    private static ParityResponse response(
            ParityResponse.Target target, int status, String body, Map<String, List<String>> headers) {
        HttpHeaders httpHeaders = HttpHeaders.of(headers, (k, v) -> true);
        return new ParityResponse(target, "http://localhost/x", status, body, httpHeaders);
    }

    private static ParityResponse html(ParityResponse.Target target, int status, String body) {
        return response(target, status, body, Map.of("Content-Type", List.of("text/html")));
    }

    private static ParityResponse json(ParityResponse.Target target, int status, String body) {
        return response(target, status, body, Map.of("Content-Type", List.of("application/json")));
    }

    private static ParityResponse redirect(ParityResponse.Target target, int status, String location) {
        return response(target, status, "", Map.of("Location", List.of(location)));
    }

    /** A realistic Symfony blog-index page, complete with per-request CSRF token and timestamp. */
    private static String blogIndexHtml(String token, String isoTimestamp) {
        return """
                <!DOCTYPE html>
                <html>
                  <body>
                    <h1>Blog</h1>
                    <article>
                      <h2>A Post</h2>
                      <time datetime="%s">published</time>
                    </article>
                    <form method="post" action="/blog/comment/a-post/new">
                      <input type="hidden" name="comment[_token]" value="%s">
                      <textarea name="comment[content]"></textarea>
                    </form>
                  </body>
                </html>
                """.formatted(isoTimestamp, token);
    }

    // ========================================================================
    // The judge PASSES on equivalent responses (differing only by legitimate noise)
    // ========================================================================

    @Nested
    @DisplayName("PASSES when responses are equivalent (only legitimate noise differs)")
    class PassesOnEquivalent {

        @Test
        @DisplayName("identical-shape pages differing only in CSRF token, timestamp and whitespace")
        void toleratesCsrfTimestampAndWhitespaceNoise() {
            var php = html(ParityResponse.Target.PHP, 200,
                    blogIndexHtml("php-token-abc123", "2024-01-31T12:34:56+00:00"));
            var java = html(ParityResponse.Target.JAVA, 200,
                    // different token, different timestamp, extra indentation/newlines
                    "  " + blogIndexHtml("java-token-zzz999", "2023-11-02T08:00:00Z")
                            .replace("<article>", "<article>\n\n   "));

            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched())
                    .as("judge must tolerate CSRF/timestamp/whitespace noise but reported: %s",
                            result.describe())
                    .isTrue();
        }

        @Test
        @DisplayName("session-id-only difference in body is tolerated")
        void toleratesSessionIdNoise() {
            var php = html(ParityResponse.Target.PHP, 200,
                    "<p>Signed in. PHPSESSID=deadbeefcafe</p>");
            var java = html(ParityResponse.Target.JAVA, 200,
                    "<p>Signed in. JSESSIONID=0011223344ff</p>");
            assertThat(ResponseComparison.compareAll(php, java).matched()).isTrue();
        }

        @Test
        @DisplayName("same redirect target on different host/port is tolerated")
        void toleratesHostPortDifferenceInRedirect() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/blog/posts/hello");
            var java = redirect(ParityResponse.Target.JAVA, 303, "http://localhost:8080/blog/posts/hello");
            assertThat(ResponseComparison.compareAll(php, java).matched()).isTrue();
        }

        @Test
        @DisplayName("JSON with same shape but different scalar values is tolerated")
        void toleratesDifferentScalarValuesInJson() {
            var php = json(ParityResponse.Target.PHP, 200,
                    "{\"id\":1,\"title\":\"Hello\",\"tags\":[\"a\",\"b\"]}");
            var java = json(ParityResponse.Target.JAVA, 200,
                    "{\"title\":\"World\",\"tags\":[\"x\",\"y\"],\"id\":42}");
            assertThat(ResponseComparison.compareAll(php, java).matched()).isTrue();
        }
    }

    // ========================================================================
    // The judge FAILS on each breakage class
    // ========================================================================

    @Nested
    @DisplayName("(a) FAILS when the status code differs (flipped status)")
    class DetectsStatusBreakage {

        @Test
        @DisplayName("200 vs 403 on otherwise-identical bodies is caught")
        void flippedStatusIsCaught() {
            String body = "<p>content</p>";
            var php = html(ParityResponse.Target.PHP, 200, body);
            var java = html(ParityResponse.Target.JAVA, 403, body);
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("status code differs");
        }

        @Test
        @DisplayName("200 vs 404 (missing resource regression) is caught")
        void notFoundRegressionIsCaught() {
            var php = html(ParityResponse.Target.PHP, 200, "<h1>Post</h1>");
            var java = html(ParityResponse.Target.JAVA, 404, "<h1>Post</h1>");
            assertThat(ResponseComparison.compareAll(php, java).matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("(b) FAILS when the redirect Location path differs")
    class DetectsRedirectBreakage {

        @Test
        @DisplayName("redirect to /blog/ vs /login is caught")
        void changedRedirectPathIsCaught() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/blog/");
            var java = redirect(ParityResponse.Target.JAVA, 303, "http://localhost:8080/login");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("redirect location path differs");
        }

        @Test
        @DisplayName("one side redirecting while the other renders is caught")
        void redirectPresenceMismatchIsCaught() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/login");
            var java = html(ParityResponse.Target.JAVA, 200, "<p>page</p>");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            // 303-vs-200 trips both the status and the redirect-presence checks.
            assertThat(result.describe()).contains("redirect presence differs");
        }
    }

    @Nested
    @DisplayName("(c) FAILS when JSON structure (key/type) differs")
    class DetectsJsonBreakage {

        @Test
        @DisplayName("renamed/dropped key is caught")
        void renamedKeyIsCaught() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1,\"title\":\"x\"}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"id\":1,\"heading\":\"x\"}");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("keys differ");
        }

        @Test
        @DisplayName("dropped field (fewer keys) is caught")
        void droppedFieldIsCaught() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1,\"title\":\"x\"}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"id\":1}");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("keys differ");
        }

        @Test
        @DisplayName("changed value type (number vs string) is caught")
        void changedValueTypeIsCaught() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"id\":\"1\"}");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("type differs");
        }

        @Test
        @DisplayName("changed array length (dropped list element) is caught")
        void changedArrayLengthIsCaught() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"posts\":[1,2,3]}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"posts\":[1,2]}");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("array length differs");
        }
    }

    @Nested
    @DisplayName("(d) FAILS when the normalized HTML body differs materially")
    class DetectsHtmlBreakage {

        @Test
        @DisplayName("different visible text (beyond noise) is caught")
        void materiallyDifferentTextIsCaught() {
            var php = html(ParityResponse.Target.PHP, 200, "<p>Welcome admin</p>");
            var java = html(ParityResponse.Target.JAVA, 200, "<p>Access denied</p>");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("normalized body structure differs");
        }

        @Test
        @DisplayName("altered body structure (extra/missing element) is caught, not masked by noise")
        void alteredStructureIsCaughtDespiteNoise() {
            // Same CSRF/timestamp noise on both sides, but Java dropped the comment form entirely.
            var php = html(ParityResponse.Target.PHP, 200,
                    blogIndexHtml("tok-a", "2024-01-31T12:34:56+00:00"));
            var java = html(ParityResponse.Target.JAVA, 200,
                    "<!DOCTYPE html><html><body><h1>Blog</h1>"
                            + "<article><h2>A Post</h2></article></body></html>");
            var result = ResponseComparison.compareAll(php, java);
            assertThat(result.matched())
                    .as("judge must see the dropped form even through noise, but said: %s",
                            result.describe())
                    .isFalse();
        }
    }

    // ========================================================================
    // Property-based coverage: the judge's verdict is driven by real divergence,
    // not by incidental input.
    // ========================================================================

    @Nested
    @DisplayName("property-based judge invariants")
    class Properties {

        /**
         * Any two <em>different</em> status codes on otherwise-identical responses must be caught
         * by the judge — no status flip escapes.
         *
         * <p>Validates: Requirements 17.3
         */
        @Property
        @DisplayName("every status-code flip is detected")
        void everyStatusFlipDetected(
                @ForAll @IntRange(min = 100, max = 599) int phpStatus,
                @ForAll @IntRange(min = 100, max = 599) int javaStatus) {
            String body = "<p>identical body</p>";
            var php = html(ParityResponse.Target.PHP, phpStatus, body);
            var java = html(ParityResponse.Target.JAVA, javaStatus, body);
            var result = ResponseComparison.compareAll(php, java);
            if (phpStatus == javaStatus) {
                assertThat(result.matched()).isTrue();
            } else {
                assertThat(result.matched()).isFalse();
            }
        }

        /**
         * For equal status codes and identical HTML skeletons, swapping in an arbitrary CSRF token
         * value never changes the verdict: the judge always reports MATCH. Legitimate noise must
         * never trip the referee.
         *
         * <p>Validates: Requirements 17.3
         */
        @Property
        @DisplayName("arbitrary CSRF token values never cause a false mismatch")
        void csrfTokenNoiseNeverFailsJudge(
                @ForAll("tokenChars") String phpToken,
                @ForAll("tokenChars") String javaToken) {
            var php = html(ParityResponse.Target.PHP, 200,
                    blogIndexHtml(phpToken, "2024-01-31T12:34:56+00:00"));
            var java = html(ParityResponse.Target.JAVA, 200,
                    blogIndexHtml(javaToken, "2024-06-15T01:02:03+00:00"));
            assertThat(ResponseComparison.compareAll(php, java).matched()).isTrue();
        }

        @net.jqwik.api.Provide
        net.jqwik.api.Arbitrary<String> tokenChars() {
            return net.jqwik.api.Arbitraries.strings()
                    .withCharRange('a', 'z')
                    .withCharRange('0', '9')
                    .ofMinLength(8)
                    .ofMaxLength(40);
        }
    }
}
