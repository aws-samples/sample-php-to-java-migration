package com.symfony.demo.repository;

import com.symfony.demo.entity.Post;
import com.symfony.demo.service.Paginator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hand-written implementation of {@link PostRepositoryCustom}. Port of the PHP
 * {@code App\Repository\PostRepository::findBySearchQuery} / {@code extractSearchTerms}.
 *
 * <p>The class name is significant: Spring Data looks for an implementation named
 * {@code <RepositoryInterface> + "Impl"} to supply the custom fragment, so this must stay
 * {@code PostRepositoryImpl} alongside {@link PostRepository}.
 *
 * <p><b>Parity with the PHP query builder:</b> the PHP method splits the query into terms, then adds
 * one {@code orWhere('p.title LIKE :t_N')} per term, orders by {@code publishedAt DESC} and caps the
 * result at {@code Paginator::PAGE_SIZE}. Because the number of terms is dynamic, the JPQL is built
 * dynamically here with the same {@code OR}-of-{@code LIKE} shape and the same ordering/limit. As in
 * PHP, terms are <em>not</em> escaped for LIKE wildcards ({@code %}/{@code _}), preserving identical
 * matching behaviour.
 */
public class PostRepositoryImpl implements PostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Post> findBySearchQuery(String query) {
        return findBySearchQuery(query, Paginator.PAGE_SIZE);
    }

    @Override
    public List<Post> findBySearchQuery(String query, int limit) {
        List<String> searchTerms = extractSearchTerms(query);
        // (extractSearchTerms is package-private static so the parity-critical tokenisation can be
        // unit tested directly, without a database.)

        // PHP: `if (0 === count($searchTerms)) { return []; }`
        if (searchTerms.isEmpty()) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder("SELECT p FROM Post p WHERE ");
        for (int i = 0; i < searchTerms.size(); i++) {
            if (i > 0) {
                jpql.append(" OR ");
            }
            jpql.append("p.title LIKE :t_").append(i);
        }
        jpql.append(" ORDER BY p.publishedAt DESC");

        TypedQuery<Post> typedQuery = entityManager.createQuery(jpql.toString(), Post.class);
        for (int i = 0; i < searchTerms.size(); i++) {
            typedQuery.setParameter("t_" + i, "%" + searchTerms.get(i) + "%");
        }
        typedQuery.setMaxResults(limit);

        return typedQuery.getResultList();
    }

    /**
     * Transforms the search string into a list of search terms, mirroring the PHP
     * {@code extractSearchTerms}: collapse runs of whitespace to a single space, trim, split on
     * spaces, de-duplicate (preserving first-seen order), and drop terms shorter than two
     * characters.
     */
    static List<String> extractSearchTerms(String searchQuery) {
        if (searchQuery == null) {
            return List.of();
        }

        String normalized = searchQuery.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        // array_unique(...) — preserve first-seen order.
        Set<String> uniqueTerms = new LinkedHashSet<>();
        for (String term : normalized.split(" ")) {
            uniqueTerms.add(term);
        }

        // array_filter(..., fn ($term) => 2 <= $term->length())
        List<String> result = new ArrayList<>();
        for (String term : uniqueTerms) {
            if (term.length() >= 2) {
                result.add(term);
            }
        }
        return result;
    }
}
