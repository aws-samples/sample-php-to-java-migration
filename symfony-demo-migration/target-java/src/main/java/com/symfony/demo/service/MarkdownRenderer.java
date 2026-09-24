package com.symfony.demo.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

/**
 * Renders Markdown to HTML, mirroring the PHP source's {@code markdown_to_html} Twig filter.
 *
 * <p>The PHP app (see {@code composer.json}) depends on {@code league/commonmark ^2.1} and
 * {@code twig/markdown-extra ^3.3}. The Twig bridge's {@code markdown_to_html} filter uses
 * league/commonmark's default {@code CommonMarkConverter} with <strong>no extensions enabled</strong>
 * (no GFM tables, autolink, strikethrough, etc.) — a search of the PHP {@code config/} and
 * {@code src/} directories found no custom converter or extension registration. This renderer
 * therefore uses commonmark-java's core CommonMark defaults, which are the Java counterpart of
 * league/commonmark's core CommonMark spec implementation.
 *
 * <p>Note: the Twig templates chain {@code |markdown_to_html|sanitize_html}. HTML sanitization is a
 * separate concern (Symfony's html-sanitizer) handled outside this service; this class reproduces
 * only the Markdown-to-HTML conversion step.
 *
 * <p>{@link Parser} and {@link HtmlRenderer} are documented as thread-safe once built, so the
 * shared singleton instances are safe to reuse across requests.
 */
@Service
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    /**
     * Parses the given CommonMark text and renders it to HTML using core CommonMark defaults.
     *
     * @param markdown the Markdown source; a {@code null} input is treated as empty
     * @return the rendered HTML (empty string for {@code null} or empty input)
     */
    public String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
