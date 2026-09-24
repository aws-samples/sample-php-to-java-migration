package com.symfony.demo.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Form-binding DTO for creating/editing blog posts. Mirrors the validation constraints declared on
 * the PHP {@code App\Entity\Post} entity (bound via {@code PostType}). Constraint messages preserve
 * the original PHP message keys so i18n parity holds once the {@code MessageSource} is wired.
 *
 * <p>PHP constraint parity:
 * <ul>
 *   <li>{@code title}: {@code @NotBlank} (default message)</li>
 *   <li>{@code summary}: {@code @NotBlank(message: 'post.blank_summary')} + {@code @Length(max: 255)}</li>
 *   <li>{@code content}: {@code @NotBlank(message: 'post.blank_content')}
 *       + {@code @Length(min: 10, minMessage: 'post.too_short_content')}</li>
 *   <li>{@code tags}: {@code @Count(max: 4, maxMessage: 'post.too_many_tags')}</li>
 * </ul>
 *
 * <p>Slug uniqueness ({@code #[UniqueEntity(fields: ['slug'], errorPath: 'title',
 * message: 'post.slug_unique')]}) is a database-level check surfaced on the {@code title} field. It
 * cannot be expressed as a Bean Validation annotation here; it is enforced later by a
 * service/repository uniqueness hook that registers the {@code post.slug_unique} error on
 * {@code title} (parity with the Doctrine {@code UniqueEntity} constraint).
 */
public class PostDto {

    @NotBlank
    private String title;

    @NotBlank(message = "post.blank_summary")
    @Size(max = 255)
    private String summary;

    @NotBlank(message = "post.blank_content")
    @Size(min = 10, message = "post.too_short_content")
    private String content;

    private LocalDateTime publishedAt;

    @Size(max = 4, message = "post.too_many_tags")
    private List<String> tags = new ArrayList<>();

    public PostDto() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
