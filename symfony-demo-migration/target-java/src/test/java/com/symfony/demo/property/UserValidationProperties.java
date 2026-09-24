package com.symfony.demo.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.symfony.demo.form.UserDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

/**
 * Property-based tests for {@link UserDto} validation parity with the PHP {@code App\Entity\User}
 * constraints (bound via {@code UserType}).
 *
 * <p>**Validates: Requirements 11.1**
 *
 * <p>Property 2: User validation parity. Using a real Jakarta Bean Validation {@link Validator}
 * (from {@link Validation#buildDefaultValidatorFactory()}), for generated {@link UserDto} inputs:
 *
 * <ul>
 *   <li>a blank {@code fullName} produces a violation on {@code fullName} ({@code @NotBlank});
 *   <li>a {@code username} shorter than 2 or longer than 50 characters produces a violation on
 *       {@code username} ({@code @Size(min = 2, max = 50)});
 *   <li>an invalid (malformed) {@code email} produces a violation on {@code email} ({@code @Email});
 *   <li>an otherwise-valid user produces no violations.
 * </ul>
 */
class UserValidationProperties {

    private static final String TAG =
            "Feature: symfony-demo-php-to-java-migration, Property 2: User validation parity";

    private static final Validator VALIDATOR;

    static {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        VALIDATOR = factory.getValidator();
    }

    private static UserDto user(String fullName, String username, String email) {
        UserDto dto = new UserDto();
        dto.setFullName(fullName);
        dto.setUsername(username);
        dto.setEmail(email);
        return dto;
    }

    private static Set<ConstraintViolation<UserDto>> violationsOn(UserDto dto, String field) {
        return VALIDATOR.validate(dto).stream()
                .filter(v -> v.getPropertyPath().toString().equals(field))
                .collect(Collectors.toSet());
    }

    // ========================================================================
    // Properties
    // ========================================================================

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A blank {@code fullName} (empty or whitespace) is rejected — {@code @NotBlank} parity.
     */
    @Property(tries = 300)
    @Tag(TAG)
    void blankFullNameProducesViolation(
            @ForAll("blank") String fullName,
            @ForAll("validUsername") String username,
            @ForAll("validEmail") String email) {

        UserDto dto = user(fullName, username, email);

        assertThat(violationsOn(dto, "fullName"))
                .as("blank fullName must be rejected")
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A {@code username} whose length is below 2 or above 50 is rejected
     * ({@code @Size(min = 2, max = 50)} parity).
     */
    @Property(tries = 400)
    @Tag(TAG)
    void outOfRangeUsernameProducesViolation(
            @ForAll("validFullName") String fullName,
            @ForAll("outOfRangeUsername") String username,
            @ForAll("validEmail") String email) {

        UserDto dto = user(fullName, username, email);

        assertThat(violationsOn(dto, "username"))
                .as("username of length %d (outside 2..50) must be rejected", username.length())
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>A malformed {@code email} is rejected — {@code @Email} parity.
     */
    @Property(tries = 300)
    @Tag(TAG)
    void invalidEmailProducesViolation(
            @ForAll("validFullName") String fullName,
            @ForAll("validUsername") String username,
            @ForAll("invalidEmail") String email) {

        UserDto dto = user(fullName, username, email);

        assertThat(violationsOn(dto, "email"))
                .as("invalid email %s must be rejected", "\"" + email + "\"")
                .isNotEmpty();
    }

    /**
     * **Validates: Requirements 11.1**
     *
     * <p>An otherwise-valid user (non-blank fullName, username length 2..50, valid email) produces
     * no violations — accept parity.
     */
    @Property(tries = 500)
    @Tag(TAG)
    void validUserHasNoViolations(
            @ForAll("validFullName") String fullName,
            @ForAll("validUsername") String username,
            @ForAll("validEmail") String email) {

        UserDto dto = user(fullName, username, email);

        assertThat(VALIDATOR.validate(dto))
                .as("valid user must produce no violations")
                .isEmpty();
    }

    // ========================================================================
    // Generators
    // ========================================================================

    /** Blank strings: empty and whitespace-only. */
    @Provide
    Arbitrary<String> blank() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "   ");
    }

    /** Non-blank full name: 1..80 alphabetic characters. */
    @Provide
    Arbitrary<String> validFullName() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(80);
    }

    /** Valid username: 2..50 alphanumeric characters. */
    @Provide
    Arbitrary<String> validUsername() {
        return Arbitraries.strings().alpha().numeric().ofMinLength(2).ofMaxLength(50);
    }

    /**
     * Out-of-range username: either too short (1 char) or too long (51..70 chars). Blank/empty is
     * excluded here so the failure is attributable to the length bound rather than {@code @NotBlank}
     * (both still land on {@code username}).
     */
    @Provide
    Arbitrary<String> outOfRangeUsername() {
        Arbitrary<String> tooShort = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(1);
        Arbitrary<String> tooLong = Arbitraries.strings().alpha().numeric().ofMinLength(51).ofMaxLength(70);
        return Arbitraries.oneOf(tooShort, tooLong);
    }

    /** Valid email addresses of the form local@domain.tld. */
    @Provide
    Arbitrary<String> validEmail() {
        Arbitrary<String> local = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(12);
        Arbitrary<String> domain = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> tld = Arbitraries.of("com", "org", "net", "io", "dev");
        return net.jqwik.api.Combinators.combine(local, domain, tld)
                .as((l, d, t) -> l + "@" + d + "." + t);
    }

    /**
     * Malformed email strings. Two reliably-invalid families are combined: (1) alphabetic strings,
     * which cannot be valid emails because they contain no {@code @}; and (2) fixed cases with an
     * empty local or domain part. These are rejected by {@code @Email} regardless of the
     * validator's leniency on borderline forms.
     */
    @Provide
    Arbitrary<String> invalidEmail() {
        Arbitrary<String> noAtSign = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> emptyParts =
                Arbitraries.of("@example.com", "@missinglocal.org", "user@", "name@", "@");
        return Arbitraries.oneOf(noAtSign, emptyParts);
    }
}
