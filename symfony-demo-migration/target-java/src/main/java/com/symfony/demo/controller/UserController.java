package com.symfony.demo.controller;

import com.symfony.demo.entity.User;
import com.symfony.demo.form.ChangePasswordDto;
import com.symfony.demo.form.UserDto;
import com.symfony.demo.repository.UserRepository;
import com.symfony.demo.security.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Authenticated user profile controller — port of the PHP {@code App\Controller\UserController}
 * ({@code #[Route('/profile'), IsGranted(User::ROLE_USER)]}). Implements profile edit and change
 * password (Requirements 8.1–8.4).
 *
 * <p><b>Authorization parity.</b> The PHP controller carried a class-level
 * {@code #[IsGranted(User::ROLE_USER)]}; here {@code @PreAuthorize("hasRole('USER')")} at class
 * level enforces the same on every handler. This is method security (the config already declares
 * {@code @EnableMethodSecurity}), and {@code ROLE_ADMIN} satisfies it via the configured role
 * hierarchy ({@code ROLE_ADMIN > ROLE_USER}). {@code SecurityConfig} additionally restricts
 * {@code /profile/**} to {@code hasRole('USER')} at the URL layer, so the two guards agree; the
 * {@code @PreAuthorize} is authoritative and independent of that URL rule.
 *
 * <p><b>Current-user resolution.</b> The authenticated principal is resolved via
 * {@code @AuthenticationPrincipal SecurityUser}, and the managed domain entity is obtained through
 * {@link SecurityUser#getDomainUser()} — the same pattern used by
 * {@link BlogController#commentNew}.
 *
 * <p>Not marked {@code final} because the class-level {@code @PreAuthorize} requires Spring to
 * create a CGLIB proxy (method security cannot subclass a final class).
 */
@Controller
@RequestMapping("/profile")
@PreAuthorize("hasRole('USER')")
public class UserController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Render the edit form — {@code GET /profile/edit} ({@code user_edit}). Prefills the bound
     * {@link UserDto} from the current user's {@code username}/{@code fullName}/{@code email},
     * mirroring the PHP {@code UserType} form built from the authenticated {@code User}.
     *
     * @param principal the authenticated user (never {@code null} thanks to {@code @PreAuthorize})
     * @param model     the view model
     * @return the {@code user/edit} view name
     */
    @GetMapping("/edit")
    public String edit(@AuthenticationPrincipal SecurityUser principal, Model model) {
        User user = principal.getDomainUser();
        model.addAttribute("user", toDto(user));
        return "user/edit";
    }

    /**
     * Handle the edit submission — {@code POST /profile/edit} ({@code user_edit}). Port of the PHP
     * {@code edit()} action: on submit+valid the current {@link User}'s fields are updated and
     * flushed ({@link UserRepository#save}), a {@code success} flash ("user.updated_successfully")
     * is added, and the response is a <b>303 SEE_OTHER</b> redirect back to {@code /profile/edit}
     * ({@code redirectToRoute('user_edit', [], Response::HTTP_SEE_OTHER)}). On validation failure
     * the {@code user/edit} view is re-rendered (HTTP 200) with the bound DTO's errors, matching
     * the PHP {@code render('user/edit.html.twig', ...)} branch.
     *
     * @param dto       the bound profile form DTO (fields {@code username}, {@code fullName},
     *                  {@code email})
     * @param binding   the binding/validation result for {@code dto}
     * @param principal the authenticated user
     * @param redirect  flash + redirect attributes
     * @return a 303 redirect to {@code /profile/edit} on success, or the edit view on failure
     */
    @PostMapping("/edit")
    public ModelAndView edit(@Valid @ModelAttribute("user") UserDto dto,
                             BindingResult binding,
                             @AuthenticationPrincipal SecurityUser principal,
                             RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            // Parity with PHP: re-render the edit form (HTTP 200) instead of redirecting. The bound
            // `user` DTO and its BindingResult are already in the implicit model.
            return new ModelAndView("user/edit");
        }

        User user = principal.getDomainUser();
        user.setUsername(dto.getUsername());
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail());
        users.save(user);

        // Symfony: $this->addFlash('success', 'user.updated_successfully').
        redirect.addFlashAttribute("flash_success", "user.updated_successfully");

        // HTTP 303 SEE_OTHER back to user_edit (Response::HTTP_SEE_OTHER in the PHP source).
        RedirectView view = new RedirectView("/profile/edit");
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return new ModelAndView(view);
    }

    /**
     * Render the change-password form — {@code GET /profile/change-password}
     * ({@code user_change_password}). Exposes an empty {@link ChangePasswordDto} for binding,
     * mirroring the PHP {@code ChangePasswordType} form.
     *
     * @param model the view model
     * @return the {@code user/change_password} view name
     */
    @GetMapping("/change-password")
    public String changePassword(Model model) {
        if (!model.containsAttribute("change_password")) {
            model.addAttribute("change_password", new ChangePasswordDto());
        }
        return "user/change_password";
    }

    /**
     * Handle the change-password submission — {@code POST /profile/change-password}
     * ({@code user_change_password}). Port of the PHP {@code changePassword()} action: on
     * submit+valid the new password is encoded and stored on the current {@link User}, flushed
     * ({@link UserRepository#save}), and then <b>the user is logged out</b> and redirected to the
     * homepage.
     *
     * <p><b>Logout parity.</b> Symfony called {@code $security->logout(validateCsrfToken: false)}
     * then redirected to {@code homepage}. Here the equivalent is performed inline: the HTTP
     * session is invalidated ({@link HttpServletRequest#getSession()} +
     * {@code invalidate()}) and the {@link SecurityContextHolder} is cleared, then a redirect to
     * {@code /blog/} (the blog index / homepage equivalent) is returned. No CSRF re-validation is
     * done here, matching {@code validateCsrfToken: false} (the POST itself is CSRF-protected by
     * the form token). A plain redirect (302) is used, matching the PHP
     * {@code redirectToRoute('homepage')} which does not set an explicit status.
     *
     * <p>On validation failure the {@code user/change_password} view is re-rendered (HTTP 200)
     * with the bound DTO's errors, matching the PHP {@code render('user/change_password.html.twig',
     * ...)} branch.
     *
     * @param dto       the bound change-password form DTO
     * @param binding   the binding/validation result for {@code dto}
     * @param principal the authenticated user
     * @param request   the current request (used to invalidate the session on logout)
     * @return a redirect to {@code /blog/} after logout on success, or the form view on failure
     */
    @PostMapping("/change-password")
    public ModelAndView changePassword(@Valid @ModelAttribute("change_password") ChangePasswordDto dto,
                                        BindingResult binding,
                                        @AuthenticationPrincipal SecurityUser principal,
                                        HttpServletRequest request) {
        if (binding.hasErrors()) {
            // Parity with PHP: re-render the form (HTTP 200) instead of redirecting.
            return new ModelAndView("user/change_password");
        }

        User user = principal.getDomainUser();
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        users.save(user);

        // Symfony: $security->logout(validateCsrfToken: false); then redirect to homepage.
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();

        return new ModelAndView(new RedirectView("/blog/"));
    }

    /** Maps the current user's persisted fields into a {@link UserDto} for GET prefill. */
    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setUsername(user.getUsername());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        return dto;
    }
}
