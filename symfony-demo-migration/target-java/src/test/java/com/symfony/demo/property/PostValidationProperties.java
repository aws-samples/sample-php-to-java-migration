package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.form.PostDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for {@link PostDto} validation parity with the PHP {@code App\Entity\Post}
 * constraints (bound via {@code PostType}).
 *
 * <p>**Validates: Requirements 11.1**
 *
 * <p>Property 1: Post validation parity. Using a real Jakarta Bean Validation {@link Validator}
 * (from {@link Validation#buildDefaultValidatorFactory()}), for generated {@link PostDto} inputs the
 * accept/reject decision and the violated constraint message keys match the PHP rules:
 *
 * <ul>
 *   <li>a blank {@code title} produces a violation on {@code title};
 *   <li>a blank {@code summary} or one longer than 255 characters produces a violation on
 *       {@code summary} (blank surfaces the {@code post.blank_summary} key);
 *   <li>a blank {@code content} or one shorter than 10 characters produces a violation on
 *       {@code content} (too-short non-blank content surfaces the {@code post.too_short_content}
 *       key);
 *   <li>more than 4 {@code tags} produces a violation on {@code tags} with the
 *       {@code post.too_many_tags} key;
 *   <li>an otherwise-valid post produces no violations.
 * </ul>
 */
class PostValidationProperties {

    private static final String TAG =
            "Feature: symfony-demo-php-to-java-migration, Property 1: Post validation parity";

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    private static PostDto post(String title, String summary, String content, List<String> tags) {
        PostDto dto = new PostDto();
        dto.setTitle(title);
        dto.setSummary(summary);
        dto.setContent(content);
        dto.setTags(tags);
        return dto;
    }

    private static Set<ConstraintViolation<PostDto>> violationsOn(PostDto dto, String field) {
        return VALIDATOR.validate(dto).stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .collect(Collectors.toSet());
    }

    private static Set<String> messagesOn(PostDto dto, String field) {
        return violationsOn(dto, field).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    // ========================================================================
    // Properties
    // ========================================================================

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A blank {@code title} (empty or whitespace) is rejected — {@code @NotBlank} parity.
     */
    @Property(tries = 300)
    @Tag(TAG)
    void blankTitleProducesViolation(
            @ForAll("blank") String title,
            @ForAll("validSummary") String summary,
            @ForAll("validContent") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(violationsOn(dto, "title"))
                .as("blank title %s must be rejected", "\"" + title + "\"")
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A blank {@code summary} is rejected and surfaces the {@code post.blank_summary} message
     * key ({@code @NotBlank(message = "post.blank_summary")} parity).
     */
    @Property(tries = 300)
    @Tag(TAG)
    void blankSummaryProducesBlankSummaryKey(
            @ForAll("validTitle") String title,
            @ForAll("blank") String summary,
            @ForAll("validContent") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(messagesOn(dto, "summary"))
                .as("blank summary must surface the post.blank_summary key")
                .contains("post.blank_summary");
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A {@code summary} longer than 255 characters is rejected ({@code @Size(max = 255)}
     * parity).
     */
    @Property(tries = 300)
    @Tag(TAG)
    void tooLongSummaryProducesViolation(
            @ForAll("validTitle") String title,
            @ForAll("tooLongSummary") String summary,
            @ForAll("validContent") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(violationsOn(dto, "summary"))
                .as("summary of length %d (>255) must be rejected", summary.length())
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A blank {@code content} is rejected and surfaces the {@code post.blank_content} key
     * ({@code @NotBlank(message = "post.blank_content")} parity).
     */
    @Property(tries = 300)
    @Tag(TAG)
    void blankContentProducesBlankContentKey(
            @ForAll("validTitle") String title,
            @ForAll("validSummary") String summary,
            @ForAll("blank") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(messagesOn(dto, "content"))
                .as("blank content must surface the post.blank_content key")
                .contains("post.blank_content");
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>Non-blank {@code content} shorter than 10 characters is rejected and surfaces the
     * {@code post.too_short_content} key ({@code @Size(min = 10, message = "post.too_short_content")}
     * parity).
     */
    @Property(tries = 300)
    @Tag(TAG)
    void tooShortContentProducesTooShortKey(
            @ForAll("validTitle") String title,
            @ForAll("validSummary") String summary,
            @ForAll("tooShortContent") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(messagesOn(dto, "content"))
                .as("non-blank content of length %d (<10) must surface post.too_short_content", content.length())
                .contains("post.too_short_content");
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>More than 4 {@code tags} is rejected and surfaces the {@code post.too_many_tags} key
     * ({@code @Size(max = 4, message = "post.too_many_tags")} parity).
     */
    @Property(tries = 300)
    @Tag(TAG)
    void tooManyTagsProducesTooManyTagsKey(
            @ForAll("validTitle") String title,
            @ForAll("validSummary") String summary,
            @ForAll("validContent") String content,
            @ForAll("tooManyTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(messagesOn(dto, "tags"))
                .as("%d tags (>4) must surface post.too_many_tags", tags.size())
                .contains("post.too_many_tags");
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>An otherwise-valid post (non-blank title, non-blank summary &le; 255, non-blank content
     * &ge; 10, &le; 4 tags) produces no violations — accept parity.
     */
    @Property(tries = 500)
    @Tag(TAG)
    void validPostHasNoViolations(
            @ForAll("validTitle") String title,
            @ForAll("validSummary") String summary,
            @ForAll("validContent") String content,
            @ForAll("validTags") List<String> tags) {

        PostDto dto = post(title, summary, content, tags);

        assertThat(VALIDATOR.validate(dto))
                .as("valid post must produce no violations")
                .isEmpty();
    }

    // ========================================================================
    // Generators
    // ========================================================================

    /** Blank strings: empty and whitespace-only (rejected by @NotBlank, accepted by @Size). */
    @Provide
    Arbitrary<String> blank() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "   ");
    }

    /** Non-blank title: 1..80 alphabetic characters. */
    @Provide
    Arbitrary<String> validTitle() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(80);
    }

    /** Non-blank summary within the 255-char limit. */
    @Provide
    Arbitrary<String> validSummary() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(255);
    }

    /** Summary longer than the 255-char limit. */
    @Provide
    Arbitrary<String> tooLongSummary() {
        return Arbitraries.strings().alpha().ofMinLength(256).ofMaxLength(320);
    }

    /** Non-blank content at least 10 characters long. */
    @Provide
    Arbitrary<String> validContent() {
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(300);
    }

    /** Non-blank content shorter than 10 characters (isolates too_short from blank_content). */
    @Provide
    Arbitrary<String> tooShortContent() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(9);
    }

    /** Valid tag lists: 0..4 non-empty tag names. */
    @Provide
    Arbitrary<List<String>> validTags() {
        Arbitrary<String> tag = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        return tag.list().ofMinSize(0).ofMaxSize(4);
    }

    /** Over-limit tag lists: 5..10 tag names. */
    @Provide
    Arbitrary<List<String>> tooManyTags() {
        Arbitrary<String> tag = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        return tag.list().ofMinSize(5).ofMaxSize(10);
    }
}
