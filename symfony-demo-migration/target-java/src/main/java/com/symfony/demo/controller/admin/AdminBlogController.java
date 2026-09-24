package com.symfony.demo.controller.admin;

import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.Tag;
import com.symfony.demo.entity.User;
import com.symfony.demo.form.PostDto;
import com.symfony.demo.repository.PostRepository;
import com.symfony.demo.repository.TagRepository;
import com.symfony.demo.security.PostPermissionEvaluator;
import com.symfony.demo.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Admin blog controller — port of the PHP {@code App\Controller\Admin\BlogController}
 * ({@code #[Route('/admin/post')] #[IsGranted(User::ROLE_ADMIN)]}). Implements admin post
 * management for the current admin's own posts: list, create, show, edit, delete
 * (Requirements 9.1–9.5).
 *
 * <p><b>Authorization parity.</b> The PHP controller carried a class-level
 * {@code #[IsGranted(User::ROLE_ADMIN)]}; here {@code @PreAuthorize("hasRole('ADMIN')")} at class
 * level enforces the same on every handler (method security is enabled via
 * {@code @EnableMethodSecurity} in {@link com.symfony.demo.config.SecurityConfig}). Per-post
 * ownership checks for show/edit/delete reproduce the PHP {@code denyAccessUnlessGranted(...)} /
 * {@code #[IsGranted('edit', subject: 'post')]} calls by invoking the same
 * {@link PostPermissionEvaluator} used by task 6.2. Because the entity must be loaded before it can
 * be authorized, the check is performed inline (throwing {@link AccessDeniedException} → HTTP 403)
 * rather than through {@code @PreAuthorize("hasPermission(#post, ...)")}, which would require the
 * post to already be a resolved method argument. The evaluator's rule is author-only (a documented
 * deviation from Requirement 10's "author OR admin"; see {@code PostPermissionEvaluator}).
 *
 * <p><b>Current-user resolution.</b> The authenticated principal is resolved via
 * {@code @AuthenticationPrincipal SecurityUser}, and the managed domain entity is obtained through
 * {@link SecurityUser#getDomainUser()} — the same pattern used by
 * {@link com.symfony.demo.controller.UserController} and
 * {@link com.symfony.demo.controller.BlogController#commentNew}.
 *
 * <p><b>CSRF deviation.</b> The PHP {@code delete()} action validated a hand-rolled CSRF token
 * named {@code delete} read from the request payload. Here delete relies on Spring Security's
 * built-in CSRF protection (the delete form authored in task 11.2 renders the standard
 * {@code _csrf} token); no custom {@code delete}-named token is implemented. This is a
 * framework-mechanics difference with no behavioral-parity impact and is recorded in
 * BEHAVIOR_CHANGES.md.
 *
 * <p>Not marked {@code final} because the class-level {@code @PreAuthorize} requires Spring to
 * create a CGLIB proxy (method security cannot subclass a final class) — the same constraint hit
 * in task 9.1.
 */
@Controller
@RequestMapping("/admin/post")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBlogController {

    private final PostRepository posts;
    private final TagRepository tags;
    private final PostPermissionEvaluator permissionEvaluator;

    public AdminBlogController(PostRepository posts, TagRepository tags,
                               PostPermissionEvaluator permissionEvaluator) {
        this.posts = posts;
        this.tags = tags;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * Lists the current admin's own posts — {@code GET /admin/post/} ({@code admin_index} /
     * {@code admin_post_index}). Port of the PHP {@code index()} action, which fetched
     * {@code findBy(['author' => $user], ['publishedAt' => 'DESC'])} and rendered
     * {@code admin/blog/index.html.twig} with a {@code posts} variable.
     *
     * @param principal the authenticated admin (never {@code null} thanks to {@code @PreAuthorize})
     * @param model     the view model
     * @return the {@code admin/blog/index} view name; model attribute {@code posts} = the author's
     *         posts (newest first)
     */
    @GetMapping("/")
    public String index(@AuthenticationPrincipal SecurityUser principal, Model model) {
        User user = principal.getDomainUser();
        model.addAttribute("posts", posts.findByAuthorOrderByPublishedAtDesc(user));
        return "admin/blog/index";
    }

    /**
     * Render the new-post form — {@code GET /admin/post/new} ({@code admin_post_new}). Exposes an
     * empty {@link PostDto} bound as {@code post} for the form, mirroring the PHP {@code new()} GET
     * branch which rendered {@code admin/blog/new.html.twig} with a fresh {@code Post}/{@code form}.
     *
     * @param model the view model
     * @return the {@code admin/blog/new} view name; model attribute {@code post} = empty
     *         {@link PostDto}
     */
    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("post")) {
            model.addAttribute("post", new PostDto());
        }
        return "admin/blog/new";
    }

    /**
     * Handle the new-post submission — {@code POST /admin/post/new} ({@code admin_post_new}). Port
     * of the PHP {@code new()} action: a new {@link Post} is created with the current user as
     * author; on submit+valid it is persisted and flushed, a {@code success} flash
     * ("post.created_successfully") is added, and the response is a <b>303 SEE_OTHER</b> redirect —
     * to {@code /admin/post/new} when the {@code saveAndCreateNew} button was clicked, otherwise to
     * {@code /admin/post/}. On validation failure the {@code admin/blog/new} view is re-rendered
     * (HTTP 200) with the bound DTO's errors.
     *
     * <p><b>Slug parity.</b> The PHP {@code PostType} set the slug on submit only when it was still
     * {@code null} ({@code $post->setSlug($this->slugger->slug($post->getTitle())->lower())}). Here
     * the slug is generated for the new post from its title via {@link #slugify(String)} (see that
     * method for the approach and its deviation note).
     *
     * @param dto       the bound post form DTO
     * @param binding   the binding/validation result for {@code dto}
     * @param principal the authenticated admin
     * @param saveAndCreateNew present (non-{@code null}) when the "save and create new" submit
     *                         button was clicked
     * @return a 303 redirect on success, or the new view on failure
     */
    @PostMapping("/new")
    @Transactional
    public ModelAndView create(@Valid @ModelAttribute("post") PostDto dto,
                               BindingResult binding,
                               @AuthenticationPrincipal SecurityUser principal,
                               @RequestParam(value = "saveAndCreateNew", required = false)
                               String saveAndCreateNew,
                               RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            // Parity with PHP: re-render the new form (HTTP 200) instead of redirecting.
            return new ModelAndView("admin/blog/new");
        }

        Post post = new Post();
        post.setAuthor(principal.getDomainUser());
        applyDto(post, dto);
        // PostType only sets the slug when it is still null (i.e. for new posts).
        post.setSlug(slugify(dto.getTitle()));
        posts.save(post);

        // Symfony: $this->addFlash('success', 'post.created_successfully').
        // saveAndCreateNew -> redirect to admin_post_new; otherwise -> admin_post_index.
        String target = saveAndCreateNew != null ? "/admin/post/new" : "/admin/post/";
        return redirect(target, "post.created_successfully", redirect);
    }

    /**
     * Show a single post — {@code GET /admin/post/{id}} ({@code admin_post_show}). Port of the PHP
     * {@code show()} action: authorizes {@link PostPermissionEvaluator#SHOW} ("Posts can only be
     * shown to their authors.") and renders {@code admin/blog/show.html.twig} with the {@code post}.
     *
     * @param id        the post id (positive integer, enforced by the path constraint)
     * @param principal the authenticated admin
     * @param auth      the current authentication (for the ownership check)
     * @param model     the view model
     * @return the {@code admin/blog/show} view name; model attribute {@code post} = the entity
     */
    @GetMapping("/{id:\\d+}")
    public String show(@PathVariable Long id,
                       @AuthenticationPrincipal SecurityUser principal,
                       Authentication auth,
                       Model model) {
        Post post = requirePost(id);
        denyUnlessGranted(auth, post, PostPermissionEvaluator.SHOW,
                "Posts can only be shown to their authors.");

        model.addAttribute("post", post);
        return "admin/blog/show";
    }

    /**
     * Render the edit form — {@code GET /admin/post/{id}/edit} ({@code admin_post_edit}). Authorizes
     * {@link PostPermissionEvaluator#EDIT} ("Posts can only be edited by their authors.") then
     * exposes the post's current values as a bound {@link PostDto} ({@code post}) plus the entity id
     * ({@code postId}) so the view can build the edit/show/delete links, mirroring the PHP
     * {@code edit()} GET branch (which passed both {@code post} and {@code form}).
     *
     * @param id        the post id (positive integer)
     * @param auth      the current authentication (for the ownership check)
     * @param model     the view model
     * @return the {@code admin/blog/edit} view name; model attributes {@code post} = prefilled
     *         {@link PostDto}, {@code postId} = the entity id
     */
    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Authentication auth, Model model) {
        Post post = requirePost(id);
        denyUnlessGranted(auth, post, PostPermissionEvaluator.EDIT,
                "Posts can only be edited by their authors.");

        if (!model.containsAttribute("post")) {
            model.addAttribute("post", toDto(post));
        }
        model.addAttribute("postId", post.getId());
        return "admin/blog/edit";
    }

    /**
     * Handle the edit submission — {@code POST /admin/post/{id}/edit} ({@code admin_post_edit}).
     * Port of the PHP {@code edit()} action: authorizes {@code edit}; on submit+valid the post's
     * fields are updated and flushed, a {@code success} flash ("post.updated_successfully") is
     * added, and the response is a <b>303 SEE_OTHER</b> redirect back to
     * {@code /admin/post/{id}/edit}. On validation failure the {@code admin/blog/edit} view is
     * re-rendered (HTTP 200) with the bound DTO's errors. The slug is <em>not</em> regenerated on
     * edit (parity with the PHP form event, which only sets the slug when it is null).
     *
     * @param id        the post id (positive integer)
     * @param dto       the bound post form DTO
     * @param binding   the binding/validation result for {@code dto}
     * @param auth      the current authentication (for the ownership check)
     * @param model     the view model (populated with {@code postId} on validation failure)
     * @return a 303 redirect to the edit page on success, or the edit view on failure
     */
    @PostMapping("/{id:\\d+}/edit")
    @Transactional
    public ModelAndView edit(@PathVariable Long id,
                             @Valid @ModelAttribute("post") PostDto dto,
                             BindingResult binding,
                             Authentication auth,
                             Model model,
                             RedirectAttributes redirect) {
        Post post = requirePost(id);
        denyUnlessGranted(auth, post, PostPermissionEvaluator.EDIT,
                "Posts can only be edited by their authors.");

        if (binding.hasErrors()) {
            // Parity with PHP: re-render the edit form (HTTP 200). The bound `post` DTO and its
            // BindingResult are already in the implicit model; the view still needs the entity id.
            model.addAttribute("postId", post.getId());
            return new ModelAndView("admin/blog/edit");
        }

        applyDto(post, dto);
        posts.save(post);

        // Symfony: $this->addFlash('success', 'post.updated_successfully'); redirect 303 to edit.
        return redirect("/admin/post/" + post.getId() + "/edit", "post.updated_successfully", redirect);
    }

    /**
     * Delete a post — {@code POST /admin/post/{id}/delete} ({@code admin_post_delete}). Port of the
     * PHP {@code delete()} action: authorizes {@code delete}, clears the post's tags
     * ({@code post.getTags().clear()}) and removes the post; the entity's {@code orphanRemoval}
     * mapping deletes the associated comments and Hibernate removes the tag join-table rows. A
     * {@code success} flash ("post.deleted_successfully") is added and the response is a
     * <b>303 SEE_OTHER</b> redirect to {@code /admin/post/}.
     *
     * <p>CSRF is handled by Spring Security's standard token (see the class-level note); no custom
     * {@code delete}-named token is validated.
     *
     * @param id   the post id (positive integer)
     * @param auth the current authentication (for the ownership check)
     * @return a 303 redirect to the admin post index
     */
    @PostMapping("/{id:\\d+}/delete")
    @Transactional
    public ModelAndView delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        Post post = requirePost(id);
        denyUnlessGranted(auth, post, PostPermissionEvaluator.DELETE, "Access Denied.");

        // Parity with PHP: clear the tags first (join-table rows), then remove the post. The
        // Post entity's orphanRemoval on comments cascades their deletion on removal.
        post.getTags().clear();
        posts.delete(post);

        return redirect("/admin/post/", "post.deleted_successfully", redirect);
    }

    /**
     * Loads a post by id or throws {@link HttpStatus#NOT_FOUND}, reproducing the 404 the PHP
     * {@code EntityValueResolver} returned when the {@code {id}} route parameter matched no post.
     */
    private Post requirePost(Long id) {
        return posts.findById(id.intValue())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * Inline ownership check mirroring the PHP {@code denyAccessUnlessGranted($attr, $post, $msg)}:
     * delegates to the shared {@link PostPermissionEvaluator} and throws
     * {@link AccessDeniedException} (→ HTTP 403) when the current user is not the post's author.
     */
    private void denyUnlessGranted(Authentication auth, Post post, String permission, String message) {
        if (!permissionEvaluator.hasPermission(auth, post, permission)) {
            throw new AccessDeniedException(message);
        }
    }

    /**
     * Copies the editable fields from the form DTO onto the entity. The {@code publishedAt} is only
     * overwritten when supplied (the {@code Post} constructor defaults it to "now" for new posts,
     * matching the PHP entity constructor + optional {@code DateTimePickerType} value). Tags are
     * resolved to {@link Tag} entities and replace the post's current tags.
     */
    private void applyDto(Post post, PostDto dto) {
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        post.setContent(dto.getContent());
        if (dto.getPublishedAt() != null) {
            post.setPublishedAt(dto.getPublishedAt());
        }
        applyTags(post, dto.getTags());
    }

    /**
     * Resolves the submitted tag names to {@link Tag} entities (reusing an existing tag with the
     * same name, or creating a new one — reproducing the PHP {@code TagsInputType} view-transformer
     * behavior) and replaces the post's tag collection. Blank/duplicate names are skipped.
     */
    private void applyTags(Post post, List<String> tagNames) {
        post.getTags().clear();
        if (tagNames == null) {
            return;
        }
        List<String> seen = new ArrayList<>();
        for (String raw : tagNames) {
            if (raw == null) {
                continue;
            }
            String name = raw.trim();
            if (name.isEmpty() || seen.contains(name)) {
                continue;
            }
            seen.add(name);
            Tag tag = tags.findByName(name).orElseGet(() -> new Tag(name));
            post.addTag(tag);
        }
    }

    /** Maps a persisted post's fields into a {@link PostDto} for GET prefill on the edit form. */
    private PostDto toDto(Post post) {
        PostDto dto = new PostDto();
        dto.setTitle(post.getTitle());
        dto.setSummary(post.getSummary());
        dto.setContent(post.getContent());
        dto.setPublishedAt(post.getPublishedAt());
        List<String> tagNames = new ArrayList<>();
        for (Tag tag : post.getTags()) {
            tagNames.add(tag.getName());
        }
        dto.setTags(tagNames);
        return dto;
    }

    /**
     * Builds a 303 SEE_OTHER redirect carrying a {@code flash_success} flash attribute. Uses
     * {@link RedirectAttributes#addFlashAttribute} (not a model attribute) so the message survives
     * the redirect in flash scope rather than leaking into the query string — the same flash key
     * convention ({@code flash_success}) established in task 10.2.
     */
    private ModelAndView redirect(String url, String flashSuccess, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flash_success", flashSuccess);
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        return new ModelAndView(view);
    }

    /**
     * Slugifies a title the way the Symfony {@code AsciiSlugger} did for the {@code PostType} form
     * event ({@code $slugger->slug($title)->lower()}): transliterate to ASCII, lower-case, and
     * join alphanumeric runs with single hyphens (trimming leading/trailing hyphens).
     *
     * <p><b>Deviation note.</b> This uses {@link Normalizer} (NFD) + diacritic stripping rather
     * than Symfony's ICU-backed transliteration, so exotic characters may transliterate slightly
     * differently; for the ASCII/Latin titles used by the demo the result matches. Recorded in
     * BEHAVIOR_CHANGES.md.
     */
    private String slugify(String title) {
        if (title == null) {
            return "";
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug;
    }
}
