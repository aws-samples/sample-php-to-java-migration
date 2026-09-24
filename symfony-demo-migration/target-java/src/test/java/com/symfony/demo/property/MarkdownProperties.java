package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.service.MarkdownRenderer;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for {@link MarkdownRenderer#toHtml(String)} — CommonMark rendering parity.
 *
 * <p>**Validates: Requirements 4.3**
 *
 * <p>Property 7: Markdown round-trip. The PHP app renders post content with
 * {@code league/commonmark}'s core CommonMark converter (no extensions). The Java port uses
 * commonmark-java's core defaults, the Java counterpart of that spec implementation. This test
 * asserts two things:
 *
 * <ul>
 *   <li><b>Fidelity</b>: for representative Markdown snippets (bold, italic, headings, lists,
 *       links, inline code, and plain text), {@link MarkdownRenderer#toHtml(String)} produces
 *       exactly the output of commonmark-java's own core {@link Parser}/{@link HtmlRenderer}
 *       defaults — proving the renderer adds/removes no behavior.</li>
 *   <li><b>Structure</b>: each construct yields the expected CommonMark HTML structure
 *       ({@code **x** -> <strong>x</strong>}, {@code # H -> <h1>}, {@code - a -> <ul><li>}, and
 *       plain text wrapped in {@code <p>}).</li>
 * </ul>
 */
class MarkdownProperties {

    private static final String TAG =
            "Feature: symfony-demo-php-to-java-migration, Property 7: Markdown round-trip";

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    /** Independent reference built from commonmark-java core defaults (the parity baseline). */
    private final Parser refParser = Parser.builder().build();
    private final HtmlRenderer refRenderer = HtmlRenderer.builder().build();

    private String reference(String markdown) {
        Node document = refParser.parse(markdown);
        return refRenderer.render(document);
    }

    // ========================================================================
    // Fidelity property: matches commonmark-java core defaults exactly
    // ========================================================================

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>For any representative Markdown snippet, the renderer's output equals the core
     * commonmark-java baseline output exactly.
     */
    @Property(tries = 500)
    @Tag(TAG)
    void rendersEqualToCommonMarkBaseline(@ForAll("markdownSnippets") String markdown) {
        assertThat(renderer.toHtml(markdown))
                .as("toHtml must match commonmark-java core defaults for input:\n%s", markdown)
                .isEqualTo(reference(markdown));
    }

    // ========================================================================
    // Structural properties: each construct yields the expected HTML shape
    // ========================================================================

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>{@code **word**} renders bold as {@code <strong>word</strong>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void boldRendersStrong(@ForAll("word") String word) {
        assertThat(renderer.toHtml("**" + word + "**"))
                .as("bold **%s** must render <strong>", word)
                .contains("<strong>" + word + "</strong>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>{@code *word*} renders italics as {@code <em>word</em>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void italicRendersEm(@ForAll("word") String word) {
        assertThat(renderer.toHtml("*" + word + "*"))
                .as("italic *%s* must render <em>", word)
                .contains("<em>" + word + "</em>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>{@code # word} renders a level-1 heading {@code <h1>word</h1>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void headingRendersH1(@ForAll("word") String word) {
        assertThat(renderer.toHtml("# " + word))
                .as("heading # %s must render <h1>", word)
                .contains("<h1>" + word + "</h1>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>{@code - word} renders an unordered list {@code <ul>...<li>word</li>...</ul>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void dashRendersUnorderedList(@ForAll("word") String word) {
        String html = renderer.toHtml("- " + word);
        assertThat(html)
                .as("list - %s must render <ul> and <li>", word)
                .contains("<ul>")
                .contains("<li>" + word + "</li>")
                .contains("</ul>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>{@code [word](http://example.com)} renders an anchor with the target href.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void linkRendersAnchor(@ForAll("word") String word) {
        assertThat(renderer.toHtml("[" + word + "](http://example.com)"))
                .as("link must render <a href>")
                .contains("<a href=\"http://example.com\">" + word + "</a>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>Inline code {@code `word`} renders {@code <code>word</code>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void inlineCodeRendersCode(@ForAll("word") String word) {
        assertThat(renderer.toHtml("`" + word + "`"))
                .as("inline code `%s` must render <code>", word)
                .contains("<code>" + word + "</code>");
    }

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>Plain text (a single alphabetic word, no Markdown syntax) is wrapped in a paragraph
     * {@code <p>word</p>}.
     */
    @Property(tries = 200)
    @Tag(TAG)
    void plainTextWrappedInParagraph(@ForAll("word") String word) {
        assertThat(renderer.toHtml(word))
                .as("plain text %s must be wrapped in <p>", word)
                .contains("<p>" + word + "</p>");
    }

    // ========================================================================
    // Edge-case examples
    // ========================================================================

    /**
     * **Validates: Requirements 4.3**
     *
     * <p>Null and empty input render to an empty string (and still agree with the baseline for the
     * empty string).
     */
    @Example
    @Tag(TAG)
    void nullAndEmptyRenderEmpty() {
        assertThat(renderer.toHtml(null)).isEmpty();
        assertThat(renderer.toHtml("")).isEmpty();
        assertThat(renderer.toHtml("")).isEqualTo(reference(""));
    }

    // ========================================================================
    // Generators
    // ========================================================================

    /** A single alphabetic word — safe from HTML escaping and Markdown metacharacters. */
    @Provide
    Arbitrary<String> word() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(15);
    }

    /**
     * Representative Markdown snippets covering the constructs the blog uses: bold, italics,
     * headings, unordered lists, links, inline code, and plain paragraphs. Content words are
     * alphabetic to avoid escaping noise; the property compares full output against the baseline so
     * any escaping is still exercised identically on both sides.
     */
    @Provide
    Arbitrary<String> markdownSnippets() {
        Arbitrary<String> w = word();
        return Combinators.combine(w, w).as(this::buildSnippet);
    }

    private String buildSnippet(String a, String b) {
        return "# "
                + a
                + "\n\n"
                + a
                + " **"
                + b
                + "** and *"
                + a
                + "* text.\n\n"
                + "- "
                + a
                + "\n- "
                + b
                + "\n\n"
                + "Here is `"
                + a
                + "` and a [link](http://example.com/"
                + b
                + ").\n";
    }
}
