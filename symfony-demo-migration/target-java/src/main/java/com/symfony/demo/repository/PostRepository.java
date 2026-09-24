package com.symfony.demo.repository;

import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.Tag;
import com.symfony.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Post}. Port of the PHP {@code App\Repository\PostRepository}.
 *
 * <p>The central query is {@link #findLatest(Tag, Pageable)}, which reproduces the PHP
 * {@code findLatest(int $page, ?Tag $tag)}:
 * <ul>
 *   <li>only posts whose {@code publishedAt} is at or before "now" (no future/scheduled posts);</li>
 *   <li>optional filter to posts that are members of a given {@link Tag};</li>
 *   <li>newest first ({@code publishedAt DESC});</li>
 *   <li>paginated (page size / metadata handled by the Paginator service, task 5.1).</li>
 * </ul>
 * A {@code LEFT JOIN} on tags mirrors the PHP query builder and {@code DISTINCT} prevents the
 * join from producing duplicate posts when a post has multiple tags.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Integer>, PostRepositoryCustom {

    /**
     * Latest published posts, optionally filtered by tag, newest first, paginated.
     * Mirrors PHP {@code PostRepository::findLatest}. Ordering is fixed in the query
     * ({@code publishedAt DESC}); callers should pass an unsorted {@link Pageable}
     * (page number + size only) so the repository owns the ordering, exactly as the
     * PHP repository did.
     *
     * @param now the reference instant; posts published after this are excluded
     * @param tag optional tag filter; when {@code null} no tag filter is applied
     */
    @Query("""
            SELECT DISTINCT p FROM Post p
            LEFT JOIN p.tags t
            WHERE p.publishedAt <= :now
            AND (:tag IS NULL OR :tag MEMBER OF p.tags)
            ORDER BY p.publishedAt DESC
            """)
    Page<Post> findLatest(@Param("now") LocalDateTime now,
                          @Param("tag") Tag tag,
                          Pageable pageable);

    /**
     * Convenience overload that supplies the current time, matching the PHP call site which
     * used {@code new \DateTimeImmutable()} inside the repository.
     *
     * @param tag optional tag filter; when {@code null} no tag filter is applied
     */
    default Page<Post> findLatest(Tag tag, Pageable pageable) {
        return findLatest(LocalDateTime.now(), tag, pageable);
    }

    /**
     * Finds a post by its unique slug (used to resolve {@code /blog/posts/{slug}}).
     */
    Optional<Post> findBySlug(String slug);

    /**
     * All posts authored by the given user, newest first. Mirrors the PHP admin
     * {@code BlogController::index}, which used
     * {@code $posts->findBy(['author' => $user], ['publishedAt' => 'DESC'])} to list the
     * current admin's own posts (task 11.1). Unlike {@link #findLatest(Tag, Pageable)} this is
     * <em>not</em> restricted to already-published posts: the admin listing shows every post the
     * user owns, including future/scheduled ones, exactly as {@code findBy} did.
     *
     * @param author the post author to filter by (the current admin)
     * @return the author's posts ordered {@code publishedAt DESC}
     */
    List<Post> findByAuthorOrderByPublishedAtDesc(User author);
}
