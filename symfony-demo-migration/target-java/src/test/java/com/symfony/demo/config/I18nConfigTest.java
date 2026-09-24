package com.symfony.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

/**
 * Verifies the {@link I18nConfig} {@link MessageSource} resolves converted PHP catalog keys against
 * the English properties catalog ({@code i18n/messages.properties}).
 */
class I18nConfigTest {

    private final MessageSource messageSource = new I18nConfig().messageSource();

    @Test
    void resolvesActionSaveInEnglish() {
        assertThat(messageSource.getMessage("action.save", null, Locale.ENGLISH))
                .isEqualTo("Save changes");
    }

    @Test
    void resolvesDottedKeysFromBothCatalogs() {
        assertThat(messageSource.getMessage("post.no_posts_found", null, Locale.ENGLISH))
                .isEqualTo("No posts found.");
        assertThat(messageSource.getMessage("paginator.previous", null, Locale.ENGLISH))
                .isEqualTo("Previous");
        // Validator key merged into the same basename.
        assertThat(messageSource.getMessage("comment.blank", null, Locale.ENGLISH))
                .isEqualTo("Please don't leave your comment blank!");
    }

    @Test
    void unknownLocaleFallsBackToEnglishDefault() {
        assertThat(messageSource.getMessage("action.save", null, Locale.JAPANESE))
                .isEqualTo("Save changes");
    }
}
