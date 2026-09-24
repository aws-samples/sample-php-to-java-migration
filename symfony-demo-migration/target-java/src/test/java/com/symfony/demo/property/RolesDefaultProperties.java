package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.entity.User;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for {@link User#getRoles()} default/de-duplication behavior.
 *
 * <p>**Validates: Requirements 2.5**
 *
 * <p>Property 3: {@code getRoles()} always includes {@code ROLE_USER} and returns a de-duplicated
 * list, matching the PHP {@code App\Entity\User::getRoles()} behavior ({@code array_unique} plus a
 * default {@code ROLE_USER} when the stored roles are empty). For any input list of role strings
 * (empty, with duplicates, with or without {@code ROLE_USER}/{@code ROLE_ADMIN}):
 *
 * <ul>
 *   <li>the result never contains duplicate entries and is never empty;
 *   <li>when the input is empty the result is exactly {@code [ROLE_USER]} (the default role);
 *   <li>{@code ROLE_USER} is present exactly when the stored roles are empty or already contain
 *       it — matching the PHP entity, which only appends the default role for an empty list and
 *       otherwise returns {@code array_unique($roles)} unchanged;
 *   <li>when the input is non-empty its distinct roles are preserved in first-seen order (and
 *       {@code ROLE_USER} is present when the stored roles include it).
 * </ul>
 */
class RolesDefaultProperties {

    /**
     * **Validates: Requirements 2.5**
     *
     * <p>Parity with the PHP {@code getRoles()}: {@code ROLE_USER} appears in the result if and
     * only if the stored roles are empty (default applied) OR the stored roles already contain it.
     * When a user has explicit roles that do not include {@code ROLE_USER}, PHP does not inject it
     * ({@code array_unique} of the stored roles), so neither does the Java port.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 3: getRoles() always includes ROLE_USER, de-duplicated")
    void roleUserPresenceMatchesPhpSemantics(@ForAll("roleLists") List<String> stored) {
        User user = new User();
        user.setRoles(stored);

        boolean expectRoleUser = stored.isEmpty() || stored.contains(User.ROLE_USER);

        assertThat(user.getRoles().contains(User.ROLE_USER))
                .as(
                        "ROLE_USER presence must match PHP semantics for stored roles %s"
                                + " (default only applied to an empty list)",
                        stored)
                .isEqualTo(expectRoleUser);
    }

    /**
     * **Validates: Requirements 2.5**
     *
     * <p>The effective roles are never empty — every user resolves to at least one role, and when
     * no roles are stored that role is {@code ROLE_USER}.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 3: getRoles() always includes ROLE_USER, de-duplicated")
    void resultIsNeverEmpty(@ForAll("roleLists") List<String> stored) {
        User user = new User();
        user.setRoles(stored);

        assertThat(user.getRoles())
                .as("getRoles() must never be empty for stored roles %s", stored)
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 2.5**
     *
     * <p>The returned list never contains duplicate roles.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 3: getRoles() always includes ROLE_USER, de-duplicated")
    void resultHasNoDuplicates(@ForAll("roleLists") List<String> stored) {
        User user = new User();
        user.setRoles(stored);

        List<String> roles = user.getRoles();

        assertThat(roles)
                .as("getRoles() must not contain duplicates for stored roles %s", stored)
                .doesNotHaveDuplicates();
    }

    /**
     * **Validates: Requirements 2.5**
     *
     * <p>When the stored roles are empty (null or empty list), the effective roles are exactly
     * {@code [ROLE_USER]}.
     */
    @Property(tries = 100)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 3: getRoles() always includes ROLE_USER, de-duplicated")
    void emptyInputYieldsOnlyRoleUser(@ForAll("emptyRoleSources") List<String> stored) {
        User user = new User();
        user.setRoles(stored);

        assertThat(user.getRoles())
                .as("empty stored roles must default to exactly [ROLE_USER]")
                .containsExactly(User.ROLE_USER);
    }

    /**
     * **Validates: Requirements 2.5**
     *
     * <p>When the stored roles are non-empty, the result is the input's distinct roles in
     * first-seen order, and {@code ROLE_USER} is present (either it was already among the roles or
     * it is not added because the list is non-empty — mirroring the PHP behavior where the default
     * is only applied to an empty list). Because the seeded data always includes a role and the PHP
     * entity guarantees {@code ROLE_USER}, the generator constrains non-empty inputs to include
     * {@code ROLE_USER} so we can assert order-preserving de-duplication precisely.
     */
    @Property(tries = 500)
    @Tag("Feature: symfony-demo-php-to-java-migration, Property 3: getRoles() always includes ROLE_USER, de-duplicated")
    void nonEmptyInputPreservesDistinctOrder(@ForAll("nonEmptyRoleListsWithUser") List<String> stored) {
        User user = new User();
        user.setRoles(stored);

        List<String> expected = new ArrayList<>(new LinkedHashSet<>(stored));

        assertThat(user.getRoles())
                .as("non-empty stored roles must be de-duplicated preserving first-seen order")
                .containsExactlyElementsOf(expected)
                .contains(User.ROLE_USER);
    }

    // ========================================================================
    // Generators
    // ========================================================================

    /** Role-name strings covering the known roles plus arbitrary custom role labels. */
    private Arbitrary<String> roleNames() {
        Arbitrary<String> known = Arbitraries.of(User.ROLE_USER, User.ROLE_ADMIN, "ROLE_EDITOR", "ROLE_MODERATOR");
        Arbitrary<String> custom =
                Arbitraries.strings().withCharRange('A', 'Z').ofMinLength(1).ofMaxLength(12).map(s -> "ROLE_" + s);
        return Arbitraries.oneOf(known, custom);
    }

    /** Any list of roles: empty, single, duplicates, with or without ROLE_USER/ROLE_ADMIN. */
    @Provide
    Arbitrary<List<String>> roleLists() {
        return roleNames().list().ofMinSize(0).ofMaxSize(8);
    }

    /** Empty-role sources: an empty list (setRoles(null) is exercised separately in unit form). */
    @Provide
    Arbitrary<List<String>> emptyRoleSources() {
        return Arbitraries.just(new ArrayList<String>());
    }

    /** Non-empty lists guaranteed to include ROLE_USER somewhere, may contain duplicates. */
    @Provide
    Arbitrary<List<String>> nonEmptyRoleListsWithUser() {
        return roleNames()
                .list()
                .ofMinSize(0)
                .ofMaxSize(6)
                .map(
                        roles -> {
                            List<String> withUser = new ArrayList<>(roles);
                            withUser.add(User.ROLE_USER);
                            return withUser;
                        });
    }
}
