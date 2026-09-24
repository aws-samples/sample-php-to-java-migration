package com.symfony.demo.repository;

import com.symfony.demo.entity.Post;

import java.util.List;

/**
 * Custom (hand-written) query fragment for {@link PostRepository}. Spring Data merges the methods
 * declared here into the {@code PostRepository} proxy at runtime, backed by
 * {@link PostRepositoryImpl}.
 *
 * <p>This exists to port the PHP {@code PostRepository::findBySearchQuery}, whose dynamic
 * {@code OR title LIKE ...} clauses (one per search term) can't be expressed as a static Spring
 * Data derived/{@code @Query} method.
 */
public interface PostRepositoryCustom {

    /**
     * Full-text-ish title search, newest first, limited to the default page size.
     * Mirrors PHP {@code findBySearchQuery(string $query)}.
     *
     * @param query the raw search string (may be {@code null}/blank)
     * @return matching posts ordered {@code publishedAt DESC}; empty when the query yields no
     *         usable terms
     */
    List<Post> findBySearchQuery(String query);

    /**
     * As {@link #findBySearchQuery(String)} but with an explicit result limit, mirroring the PHP
     * {@code findBySearchQuery(string $query, int $limit)} signature.
     *
     * @param query the raw search string (may be {@code null}/blank)
     * @param limit maximum number of results to return
     * @return matching posts ordered {@code publishedAt DESC}, capped at {@code limit}
     */
    List<Post> findBySearchQuery(String query, int limit);
}
