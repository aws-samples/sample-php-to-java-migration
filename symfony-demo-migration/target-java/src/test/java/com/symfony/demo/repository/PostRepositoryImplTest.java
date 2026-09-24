package com.symfony.demo.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the parity-critical search-term tokenisation in {@link PostRepositoryImpl},
 * ported from the PHP {@code PostRepository::extractSearchTerms}. These cover the pure string logic
 * (whitespace collapsing, trimming, de-duplication, and the two-character minimum) without touching
 * the database; the JPQL query itself is exercised by the integration/parity tasks (16.1, 17.1).
 *
 * <p>Validates Requirements 6.1, 6.2 (search behaviour parity).
 */
class PostRepositoryImplTest {

    @Test
    @DisplayName("splits on whitespace and keeps terms of length >= 2")
    void splitsAndKeepsLongEnoughTerms() {
        assertThat(PostRepositoryImpl.extractSearchTerms("lorem ipsum"))
                .containsExactly("lorem", "ipsum");
    }

    @Test
    @DisplayName("collapses runs of whitespace (spaces, tabs, newlines) to single separators")
    void collapsesWhitespace() {
        assertThat(PostRepositoryImpl.extractSearchTerms("  lorem\t\t  ipsum \n dolor  "))
                .containsExactly("lorem", "ipsum", "dolor");
    }

    @Test
    @DisplayName("drops terms shorter than two characters")
    void dropsShortTerms() {
        assertThat(PostRepositoryImpl.extractSearchTerms("a lorem b ipsum"))
                .containsExactly("lorem", "ipsum");
    }

    @Test
    @DisplayName("de-duplicates repeated terms, preserving first-seen order")
    void deduplicatesPreservingOrder() {
        assertThat(PostRepositoryImpl.extractSearchTerms("lorem ipsum lorem dolor ipsum"))
                .containsExactly("lorem", "ipsum", "dolor");
    }

    @Test
    @DisplayName("returns no terms for null, blank, or all-too-short input")
    void returnsEmptyForNoUsableTerms() {
        assertThat(PostRepositoryImpl.extractSearchTerms(null)).isEmpty();
        assertThat(PostRepositoryImpl.extractSearchTerms("")).isEmpty();
        assertThat(PostRepositoryImpl.extractSearchTerms("   ")).isEmpty();
        assertThat(PostRepositoryImpl.extractSearchTerms("a b c")).isEmpty();
    }

    @Test
    @DisplayName("keeps a single usable term")
    void keepsSingleTerm() {
        assertThat(PostRepositoryImpl.extractSearchTerms("lorem")).containsExactly("lorem");
    }
}
