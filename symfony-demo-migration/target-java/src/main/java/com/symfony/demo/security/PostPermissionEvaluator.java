package com.symfony.demo.security;

import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Spring Security {@link PermissionEvaluator} that reproduces the Symfony
 * {@code App\Security\PostVoter} for the {@code show}/{@code edit}/{@code delete} actions on a
 * {@link Post}. Wired into method security via the {@code MethodSecurityExpressionHandler} bean in
 * {@code SecurityConfig}, so controllers express the check as
 * {@code @PreAuthorize("hasPermission(#post, 'edit')")}.
 *
 * <p><b>Behavior parity — author-only (identity equality).</b> The real PHP {@code PostVoter}
 * grants a permission <em>iff the logged-in user IS the post's author</em>, using PHP identity
 * equality ({@code $user === $post->getAuthor()}); it does <em>not</em> grant by role. This port
 * is behavior-first and reproduces that author-only rule exactly. Admins in the demo manage only
 * their own posts because both the admin controller and this voter are author-scoped.
 *
 * <p>Note: Requirement 10 (and the design's "Property 4") describe the rule as "author OR admin".
 * That wording is superseded by the actual PHP source of truth, which is author-only. This
 * discrepancy is recorded for {@code BEHAVIOR_CHANGES.md} (task 19.3).
 *
 * <p><b>Identity comparison.</b> Doctrine's per-request identity map makes {@code ===} an
 * effective compare-by-primary-key. Because the Java domain {@link User} is loaded per request
 * (JPA does not guarantee a single instance across contexts), we do not rely on JVM reference
 * equality: identity is compared by database id when both ids are present, falling back to the
 * unique {@code username} otherwise.
 */
@Component
public class PostPermissionEvaluator implements PermissionEvaluator {

    /** Permission attribute for viewing a post (PHP {@code PostVoter::SHOW}). */
    public static final String SHOW = "show";

    /** Permission attribute for editing a post (PHP {@code PostVoter::EDIT}). */
    public static final String EDIT = "edit";

    /** Permission attribute for deleting a post (PHP {@code PostVoter::DELETE}). */
    public static final String DELETE = "delete";

    /** The three attributes this evaluator supports, matching {@code PostVoter::supports()}. */
    private static final Set<String> SUPPORTED = Set.of(SHOW, EDIT, DELETE);

    /**
     * Object-based permission check used by {@code hasPermission(#post, 'edit')}.
     *
     * <p>Mirrors {@code PostVoter}: supports only {@link Post} subjects and the show/edit/delete
     * attributes; denies when there is no authenticated user; otherwise grants iff the current
     * user is the post's author.
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject,
            Object permission) {
        if (!(targetDomainObject instanceof Post post) || !(permission instanceof String attribute)) {
            return false;
        }
        // supports(): only the three post attributes are handled by this voter.
        if (!SUPPORTED.contains(attribute)) {
            return false;
        }
        User currentUser = currentUser(authentication);
        // "the user must be logged in; if not, deny permission".
        if (currentUser == null) {
            return false;
        }
        // "if the logged-in user is the author of the given blog post, grant permission".
        return isSameUser(currentUser, post.getAuthor());
    }

    /**
     * Id-based permission check (part of the {@link PermissionEvaluator} contract). Unused by the
     * PostVoter parity — authorization always operates on a loaded {@link Post} instance — so it
     * always denies rather than loading state, keeping the fail-closed behavior of the PHP voter.
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
            String targetType, Object permission) {
        return false;
    }

    /** Resolves the authenticated domain {@link User}, or {@code null} when not logged in. */
    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        // Anonymous authentication carries a plain String principal ("anonymousUser").
        if (authentication.getPrincipal() instanceof SecurityUser securityUser) {
            return securityUser.getDomainUser();
        }
        return null;
    }

    /**
     * Compares two users by identity the way Doctrine's identity map does for {@code ===}: equal
     * database ids mean the same user. Falls back to the unique {@code username} when an id is not
     * available (e.g. a not-yet-persisted author), and denies on any null.
     */
    private boolean isSameUser(User currentUser, User author) {
        if (currentUser == null || author == null) {
            return false;
        }
        if (currentUser.getId() != null && author.getId() != null) {
            return currentUser.getId().equals(author.getId());
        }
        return Objects.equals(currentUser.getUsername(), author.getUsername());
    }
}
