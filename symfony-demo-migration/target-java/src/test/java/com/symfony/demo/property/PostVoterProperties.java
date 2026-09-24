package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import com.symfony.demo.security.PostPermissionEvaluator;
import com.symfony.demo.security.SecurityUser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Property-based tests for {@link PostPermissionEvaluator}, the Spring Security
 * {@code PermissionEvaluator} that ports Symfony's {@code App\Security\PostVoter}.
 *
 * <p>**Validates: Requirements 10.1**
 *
 * <p><b>Property 4 (corrected): PostVoter grants {@code show}/{@code edit}/{@code delete} iff the
 * current user is the post's author.</b> The PHP {@code PostVoter} is AUTHOR-ONLY — it grants a
 * permission iff the authenticated user IS the post's author ({@code $user === $post->getAuthor()}),
 * and denies otherwise, including for admins who are not the author and for unauthenticated
 * requests. It does <em>not</em> grant by role.
 *
 * <p>This supersedes the design.md "Property 4: edit/delete granted iff author OR admin" wording:
 * the PHP source of truth is author-only, so these properties assert author-only behavior. This
 * corrected interpretation is recorded for {@code BEHAVIOR_CHANGES.md} (task 19.3).
 *
 * <p>Identity is compared by database id when both ids are present, otherwise by the unique
 * {@code username} (mirroring Doctrine's identity-map {@code ===}).
 */
class PostVoterProperties {

    private final PostPermissionEvaluator evaluator = new PostPermissionEvaluator();

    private static final List<String> SUPPORTED =
            List.of(PostPermissionEvaluator.SHOW, PostPermissionEvaluator.EDIT, PostPermissionEvaluator.DELETE);

    // ========================================================================
    // Property 4 (corrected): grant iff current user is the author
    // ========================================================================

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>For a supported attribute (show/edit/delete), the author of a post is always granted the
     * permission on their own post.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void authorIsAlwaysGranted(
            @ForAll("users") User author, @ForAll("supportedAttributes") String attribute) {
        Post post = postBy(author);
        Authentication auth = authFor(author);

        assertThat(evaluator.hasPermission(auth, post, attribute))
                .as("author must be granted '%s' on their own post", attribute)
                .isTrue();
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>A user who is NOT the author is denied — even when that user holds {@code ROLE_ADMIN}.
     * This is the key author-only assertion: role does not grant access.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void nonAuthorIsDeniedEvenAsAdmin(
            @ForAll("distinctUserPairs") User[] pair,
            @ForAll("supportedAttributes") String attribute,
            @ForAll boolean currentIsAdmin) {
        User currentUser = pair[0];
        User authorUser = pair[1];
        if (currentIsAdmin) {
            currentUser.setRoles(List.of(User.ROLE_ADMIN));
        }
        Post post = postBy(authorUser);
        Authentication auth = authFor(currentUser);

        assertThat(evaluator.hasPermission(auth, post, attribute))
                .as("non-author (admin=%s) must be denied '%s'", currentIsAdmin, attribute)
                .isFalse();
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>The general biconditional: {@code hasPermission == true} iff the current user's identity
     * (id when present, else username) matches the post author's, across same/different users and
     * arbitrary roles.
     */
    @Property(tries = 1000)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void grantedIffCurrentUserIsAuthor(
            @ForAll("distinctUserPairs") User[] pair,
            @ForAll boolean sameUser,
            @ForAll("supportedAttributes") String attribute) {
        User currentUser = pair[0];
        User authorUser = sameUser ? currentUser : pair[1];
        Post post = postBy(authorUser);
        Authentication auth = authFor(currentUser);

        assertThat(evaluator.hasPermission(auth, post, attribute))
                .as("grant must hold iff current user is author (sameUser=%s)", sameUser)
                .isEqualTo(sameUser);
    }

    // ========================================================================
    // Unauthenticated / anonymous → always deny
    // ========================================================================

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>A {@code null} Authentication is denied for any supported attribute, even against a valid
     * post — matching the PHP "must be logged in" guard.
     */
    @Property(tries = 200)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void nullAuthenticationIsDenied(
            @ForAll("users") User author, @ForAll("supportedAttributes") String attribute) {
        Post post = postBy(author);

        assertThat(evaluator.hasPermission(null, post, attribute))
                .as("null authentication must be denied '%s'", attribute)
                .isFalse();
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>An anonymous token (principal is the {@code "anonymousUser"} String, not a
     * {@link SecurityUser}) is treated as unauthenticated and denied.
     */
    @Property(tries = 200)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void anonymousAuthenticationIsDenied(
            @ForAll("users") User author, @ForAll("supportedAttributes") String attribute) {
        Post post = postBy(author);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        Authentication anonymous =
                new AnonymousAuthenticationToken("key", "anonymousUser", authorities);

        assertThat(evaluator.hasPermission(anonymous, post, attribute))
                .as("anonymous authentication must be denied '%s'", attribute)
                .isFalse();
    }

    // ========================================================================
    // Unsupported attributes / non-Post targets → deny
    // ========================================================================

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>Attributes outside {show, edit, delete} are unsupported and denied, even for the author
     * (mirrors {@code PostVoter::supports()}).
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void unsupportedAttributesAreDenied(
            @ForAll("users") User author, @ForAll("unsupportedAttributes") String attribute) {
        Post post = postBy(author);
        Authentication auth = authFor(author);

        assertThat(evaluator.hasPermission(auth, post, attribute))
                .as("unsupported attribute '%s' must be denied even for the author", attribute)
                .isFalse();
    }

    /**
     * **Validates: Requirements 10.1**
     *
     * <p>Non-{@link Post} target objects are denied for any attribute, even for an authenticated
     * user (the evaluator only supports {@code Post} subjects).
     */
    @Property(tries = 300)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 4: PostVoter grants show/edit/delete iff current user is the author, Validates: Requirements 10.1")
    void nonPostTargetsAreDenied(
            @ForAll("users") User author,
            @ForAll("supportedAttributes") String attribute,
            @ForAll("nonPostTargets") Object target) {
        Authentication auth = authFor(author);

        assertThat(evaluator.hasPermission(auth, target, attribute))
                .as("non-Post target %s must be denied '%s'", target, attribute)
                .isFalse();
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Builds an authenticated token backed by a {@link SecurityUser} for the given user. */
    private Authentication authFor(User user) {
        SecurityUser principal = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(
                principal, "password", principal.getAuthorities());
    }

    /** Builds a post authored by the given user. */
    private Post postBy(User author) {
        Post post = new Post();
        post.setTitle("Title");
        post.setSlug("slug");
        post.setSummary("summary");
        post.setContent("content");
        post.setAuthor(author);
        return post;
    }

    private static User user(Integer id, String username, List<String> roles) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setPassword("hash");
        user.setRoles(roles);
        if (id != null) {
            setId(user, id);
        }
        return user;
    }

    /** {@link User#id} has no setter (JPA-generated); set it reflectively to exercise id parity. */
    private static void setId(User user, Integer id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to set User.id for test", e);
        }
    }

    // ========================================================================
    // Generators
    // ========================================================================

    @Provide
    Arbitrary<String> supportedAttributes() {
        return Arbitraries.of(SUPPORTED.toArray(new String[0]));
    }

    @Provide
    Arbitrary<String> unsupportedAttributes() {
        Arbitrary<String> known = Arbitraries.of("create", "list", "publish", "view", "EDIT", "");
        Arbitrary<String> random =
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(1)
                        .ofMaxLength(10)
                        .filter(s -> !SUPPORTED.contains(s));
        return Arbitraries.oneOf(known, random);
    }

    @Provide
    Arbitrary<Object> nonPostTargets() {
        return Arbitraries.of(
                "a string",
                Integer.valueOf(42),
                new Object(),
                user(1, "someuser", List.of(User.ROLE_USER)));
    }

    private Arbitrary<String> usernames() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(12);
    }

    private Arbitrary<List<String>> roleLists() {
        Arbitrary<String> roleNames =
                Arbitraries.of(User.ROLE_USER, User.ROLE_ADMIN, "ROLE_EDITOR");
        return roleNames.list().ofMinSize(0).ofMaxSize(3);
    }

    /** Arbitrary user with an optional id (sometimes null to exercise the username fallback). */
    @Provide
    Arbitrary<User> users() {
        Arbitrary<Integer> ids = Arbitraries.oneOf(
                Arbitraries.just((Integer) null), Arbitraries.integers().between(1, 1000));
        return Combinators.combine(ids, usernames(), roleLists())
                .as(PostVoterProperties::user);
    }

    /**
     * A pair of genuinely distinct users: different usernames AND, when ids are present, different
     * ids — so identity comparison denies regardless of which path (id or username) is taken.
     */
    @Provide
    Arbitrary<User[]> distinctUserPairs() {
        Arbitrary<Boolean> useIds = Arbitraries.of(true, false);
        return Combinators.combine(usernames(), usernames(), useIds, roleLists(), roleLists())
                .as(
                        (u1, u2, withIds, r1, r2) -> {
                            String currentName = u1;
                            String otherName = u2.equals(u1) ? u2 + "x" : u2;
                            Integer currentId = withIds ? 1 : null;
                            Integer otherId = withIds ? 2 : null;
                            User current = user(currentId, currentName, new ArrayList<>(r1));
                            User other = user(otherId, otherName, new ArrayList<>(r2));
                            return new User[] {current, other};
                        });
    }
}
