package com.symfony.demo.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding DTO for creating blog comments. Mirrors the validation constraints declared on the
 * PHP {@code App\Entity\Comment} entity (bound via {@code CommentType}). Constraint messages
 * preserve the original PHP message keys so i18n parity holds once the {@code MessageSource} is
 * wired.
 *
 * <p>PHP constraint parity:
 * <ul>
 *   <li>{@code content}: {@code @NotBlank(message: 'comment.blank')}
 *       + {@code @Length(min: 5, max: 10000, minMessage: 'comment.too_short',
 *       maxMessage: 'comment.too_long')}</li>
 *   <li>{@code isLegitComment()}: {@code @Assert\IsTrue(message: 'comment.is_spam')} — the spam
 *       rule from {@code Comment::isLegitComment()} rejecting content containing {@code '@'}</li>
 * </ul>
 *
 * <p>Because Bean Validation {@code @Size} carries a single message, the min/max bounds are split
 * into two {@code @Size} constraints to preserve the distinct {@code comment.too_short} and
 * {@code comment.too_long} message keys, matching the PHP {@code minMessage}/{@code maxMessage}.
 */
public class CommentDto {

    @NotBlank(message = "comment.blank")
    @Size(min = 5, message = "comment.too_short")
    @Size(max = 10000, message = "comment.too_long")
    private String content;

    public CommentDto() {
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Reproduces the PHP {@code Comment::isLegitComment()} spam check: content must not contain the
     * {@code '@'} character. Mirrors {@code #[Assert\IsTrue(message: 'comment.is_spam')]}. Null
     * content is treated as legit (the {@code @NotBlank} constraint handles emptiness), matching the
     * PHP behavior where {@code u(null)->indexOf('@')} yields no match.
     */
    @AssertTrue(message = "comment.is_spam")
    public boolean isLegitComment() {
        if (content == null) {
            return true;
        }
        return !content.contains("@");
    }
}
