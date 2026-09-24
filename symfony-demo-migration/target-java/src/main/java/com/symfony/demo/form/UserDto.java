package com.symfony.demo.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding DTO for editing a user profile. Mirrors the validation constraints declared on the
 * PHP {@code App\Entity\User} entity (bound via {@code UserType}). Constraint messages use the
 * Bean Validation defaults, matching the PHP entity which declares no custom messages on these
 * fields.
 *
 * <p>PHP constraint parity:
 * <ul>
 *   <li>{@code fullName}: {@code @NotBlank}</li>
 *   <li>{@code username}: {@code @NotBlank} + {@code @Length(min: 2, max: 50)}</li>
 *   <li>{@code email}: {@code @Email}</li>
 * </ul>
 *
 * <p>Uniqueness of {@code username} and {@code email} is a database-level constraint (the columns
 * are declared {@code unique: true} on the entity). It is not expressible as a Bean Validation
 * annotation here and is enforced later by a service/repository uniqueness hook.
 */
public class UserDto {

    @NotBlank
    private String fullName;

    @NotBlank
    @Size(min = 2, max = 50)
    private String username;

    @Email
    private String email;

    public UserDto() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
