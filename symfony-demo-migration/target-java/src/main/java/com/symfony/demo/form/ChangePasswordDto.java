package com.symfony.demo.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding DTO for changing a user's password. Mirrors the validation constraints declared in
 * the PHP {@code ChangePasswordType} form.
 *
 * <p>PHP constraint parity:
 * <ul>
 *   <li>{@code newPassword} ({@code RepeatedType} of {@code PasswordType}):
 *       {@code new NotBlank()} + {@code new Length(min: 5, max: 128)}</li>
 *   <li>The {@code RepeatedType} requires the repeated entry to match — reproduced here by the
 *       {@code newPassword}/{@code confirmPassword} match check.</li>
 * </ul>
 *
 * <p>The PHP {@code currentPassword} field carries a {@code UserPassword} constraint that verifies
 * the value against the authenticated user's stored password. That is a security-context check
 * (not a static field constraint); it is bound here for form submission and verified at the
 * service layer against the current {@code UserDetails}.
 */
public class ChangePasswordDto {

    private String currentPassword;

    @NotBlank
    @Size(min = 5, max = 128)
    private String newPassword;

    private String confirmPassword;

    public ChangePasswordDto() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    /**
     * Reproduces the {@code RepeatedType} match requirement: the confirmation must equal the new
     * password. The empty/blank case is left to {@code @NotBlank} on {@code newPassword} so a single
     * blank submission does not produce a spurious mismatch error.
     */
    @AssertTrue(message = "password.mismatch")
    public boolean isPasswordsMatch() {
        if (newPassword == null || newPassword.isEmpty()) {
            return true;
        }
        return newPassword.equals(confirmPassword);
    }
}
