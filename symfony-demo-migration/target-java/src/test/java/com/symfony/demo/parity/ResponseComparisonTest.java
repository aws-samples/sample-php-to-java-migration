package com.symfony.demo.parity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseComparison} and its normalization helpers.
 *
 * <p>These are plain JVM tests — they do <b>not</b> require Docker or a running PHP/Java target,
 * so they always execute and guard the comparison logic that the (environment-gated) parity
 * tests depend on. They cover status-code comparison, redirect-location (path-normalized)
 * comparison, HTML normalization (Symfony CSRF {@code _token} / session id / timestamp /
 * whitespace stripping), JSON-aware structural comparison, and cookie-name parity.
 */
class ResponseComparisonTest {

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

    @Nested
    @DisplayName("status code")
    class StatusCode {

        @Test
        void identicalStatusMatches() {
            var result = ResponseComparison.compareStatusCode(
                    html(ParityResponse.Target.PHP, 200, "a"),
                    html(ParityResponse.Target.JAVA, 200, "b"));
            assertThat(result.matched()).isTrue();
        }

        @Test
        void differingStatusReported() {
            var result = ResponseComparison.compareStatusCode(
                    html(ParityResponse.Target.PHP, 200, "a"),
                    html(ParityResponse.Target.JAVA, 303, "b"));
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("PHP=200").contains("JAVA=303");
        }
    }

    @Nested
    @DisplayName("redirect location (path-normalized)")
    class RedirectLocation {

        private static ParityResponse redirect(ParityResponse.Target target, int status, String location) {
            return response(target, status, "",
                    Map.of("Location", List.of(location)));
        }

        @Test
        void samePathDifferentHostAndPortMatches() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/blog/posts/hello");
            var java = redirect(ParityResponse.Target.JAVA, 303, "http://localhost:8080/blog/posts/hello");
            assertThat(ResponseComparison.compareRedirectLocation(php, java).matched()).isTrue();
        }

        @Test
        void differentPathReported() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/blog/");
            var java = redirect(ParityResponse.Target.JAVA, 303, "http://localhost:8080/login");
            var result = ResponseComparison.compareRedirectLocation(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("/blog/").contains("/login");
        }

        @Test
        void nonRedirectsAreANoOpMatch() {
            var php = html(ParityResponse.Target.PHP, 200, "<p>ok</p>");
            var java = html(ParityResponse.Target.JAVA, 200, "<p>ok</p>");
            assertThat(ResponseComparison.compareRedirectLocation(php, java).matched()).isTrue();
        }

        @Test
        void oneSideRedirectingIsReported() {
            var php = redirect(ParityResponse.Target.PHP, 303, "http://localhost:8000/login");
            var java = html(ParityResponse.Target.JAVA, 200, "<p>page</p>");
            var result = ResponseComparison.compareRedirectLocation(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("redirect presence differs");
        }
    }

    @Nested
    @DisplayName("normalization")
    class Normalization {

        @Test
        void stripsSymfonyCsrfTokenSoTwoTokensCompareEqual() {
            String phpBody =
                    "<form><input type=\"hidden\" name=\"comment[_token]\" value=\"abc123\"></form>";
            String javaBody =
                    "<form><input type=\"hidden\" name=\"comment[_token]\" value=\"zzz999\"></form>";
            assertThat(ResponseComparison.normalize(phpBody))
                    .isEqualTo(ResponseComparison.normalize(javaBody));
        }

        @Test
        void stripsCsrfTokenFieldNamed_csrf_token() {
            String a = "<input name=\"_csrf_token\" value=\"tok-one\">";
            String b = "<input name=\"_csrf_token\" value=\"tok-two\">";
            assertThat(ResponseComparison.normalize(a))
                    .isEqualTo(ResponseComparison.normalize(b));
        }

        @Test
        void stripsSessionIdentifiers() {
            assertThat(ResponseComparison.normalize("set PHPSESSID=deadbeef here"))
                    .isEqualTo(ResponseComparison.normalize("set JSESSIONID=cafebabe here"));
        }

        @Test
        void stripsRssPubDate() {
            String php = "<item><pubDate>Tue, 01 Jan 2024 10:00:00 +0000</pubDate></item>";
            String java = "<item><pubDate>Wed, 15 May 2024 23:59:59 +0000</pubDate></item>";
            assertThat(ResponseComparison.normalize(php))
                    .isEqualTo(ResponseComparison.normalize(java));
        }

        @Test
        void stripsIsoTimestamps() {
            String php = "<time>2024-01-31T12:34:56+00:00</time>";
            String java = "<time>2023-11-02T08:00:00Z</time>";
            assertThat(ResponseComparison.normalize(php))
                    .isEqualTo(ResponseComparison.normalize(java));
        }

        @Test
        void collapsesWhitespaceAndComments() {
            String withNoise = "<div>   hello\n\n<!-- framework note -->  world </div>";
            String clean = "<div> hello world </div>";
            assertThat(ResponseComparison.normalize(withNoise))
                    .isEqualTo(ResponseComparison.normalize(clean));
        }

        @Test
        void nullBodyNormalizesToEmpty() {
            assertThat(ResponseComparison.normalize(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("body structure")
    class BodyStructure {

        @Test
        void htmlBodiesDifferingOnlyByTokenAreStructurallyEqual() {
            var php = html(ParityResponse.Target.PHP, 200,
                    "<p>Hi</p><input name=\"post[_token]\" value=\"aaa\">");
            var java = html(ParityResponse.Target.JAVA, 200,
                    "<p>Hi</p>\n<input name=\"post[_token]\"  value=\"bbb\">");
            assertThat(ResponseComparison.compareBodyStructure(php, java).matched()).isTrue();
        }

        @Test
        void materiallyDifferentHtmlReported() {
            var php = html(ParityResponse.Target.PHP, 200, "<p>Welcome admin</p>");
            var java = html(ParityResponse.Target.JAVA, 200, "<p>Access denied</p>");
            assertThat(ResponseComparison.compareBodyStructure(php, java).matched()).isFalse();
        }
    }

    @Nested
    @DisplayName("JSON structural comparison")
    class Json {

        @Test
        void sameShapeDifferentScalarValuesMatch() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1,\"title\":\"Hello\"}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"title\":\"World\",\"id\":42}");
            assertThat(ResponseComparison.compareBodyStructure(php, java).matched()).isTrue();
        }

        @Test
        void differentKeysReported() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"identifier\":1}");
            var result = ResponseComparison.compareBodyStructure(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("keys differ");
        }

        @Test
        void differentValueTypeReported() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"id\":1}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"id\":\"1\"}");
            var result = ResponseComparison.compareBodyStructure(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("type differs");
        }

        @Test
        void differentArrayLengthReported() {
            var php = json(ParityResponse.Target.PHP, 200, "{\"posts\":[1,2,3]}");
            var java = json(ParityResponse.Target.JAVA, 200, "{\"posts\":[1,2]}");
            var result = ResponseComparison.compareBodyStructure(php, java);
            assertThat(result.matched()).isFalse();
            assertThat(result.describe()).contains("array length differs");
        }
    }

    @Nested
    @DisplayName("cookie names")
    class Cookies {

        @Test
        void sameCookieNamesMatchDespiteDifferentValues() {
            var php = response(ParityResponse.Target.PHP, 200, "",
                    Map.of("Set-Cookie", List.of("PHPSESSID=abc; Path=/", "locale=en")));
            var java = response(ParityResponse.Target.JAVA, 200, "",
                    Map.of("Set-Cookie", List.of("PHPSESSID=xyz; Path=/; HttpOnly", "locale=fr")));
            assertThat(ResponseComparison.compareCookieNames(php, java).matched()).isTrue();
        }

        @Test
        void differingCookieNamesReported() {
            var php = response(ParityResponse.Target.PHP, 200, "",
                    Map.of("Set-Cookie", List.of("PHPSESSID=abc")));
            var java = response(ParityResponse.Target.JAVA, 200, "",
                    Map.of("Set-Cookie", List.of("JSESSIONID=xyz")));
            assertThat(ResponseComparison.compareCookieNames(php, java).matched()).isFalse();
        }
    }

    @Test
    @DisplayName("compareAll combines status, redirect and body checks")
    void compareAllAggregatesDifferences() {
        var php = html(ParityResponse.Target.PHP, 200, "<p>same</p>");
        var java = html(ParityResponse.Target.JAVA, 500, "<p>different</p>");
        var result = ResponseComparison.compareAll(php, java);
        assertThat(result.matched()).isFalse();
        // status differs + body differs (both non-redirects, so redirect check is a match)
        assertThat(result.differences()).hasSize(2);
    }
}
