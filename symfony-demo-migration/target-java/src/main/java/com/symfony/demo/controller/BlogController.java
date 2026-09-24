package com.symfony.demo.controller;

import com.symfony.demo.entity.Comment;
import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.Tag;
import com.symfony.demo.event.CommentCreatedEvent;
import com.symfony.demo.form.CommentDto;
import com.symfony.demo.repository.CommentRepository;
import com.symfony.demo.repository.PostRepository;
import com.symfony.demo.repository.TagRepository;
import com.symfony.demo.security.SecurityUser;
import com.symfony.demo.service.MarkdownRenderer;
import com.symfony.demo.service.Paginator;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
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
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * Public blog controller — port of the PHP {@code App\Controller\BlogController}
 * ({@code #[Route('/blog')]}).
 *
 * <p>This class implements the public listing surface (Requirements 3.1–3.4): the paginated
 * blog index, the {@code /blog/page/{page}} paginated variant, the optional {@code ?tag=} filter,
 * and the {@code /blog/rss.xml} feed. It also serves post detail ({@code /blog/posts/{slug}},
 * Requirements 4.1–4.3) and search ({@code /blog/search}, Requirements 6.1–6.2). The authenticated
 * comment POST ({@code /blog/comment/{postSlug}/new}) is task 9.1 and intentionally left out here.
 *
 * <p><b>Routing parity</b> with the PHP {@code index()} action, which declared three routes on one
 * method using {@code _format}/{@code page} defaults:
 * <ul>
 *   <li>{@code GET /blog/} — {@code blog_index}, default {@code page=1}, {@code _format=html};</li>
 *   <li>{@code GET /blog/page/{page}} — {@code blog_index_paginated}, {@code page} a positive int,
 *       {@code _format=html};</li>
 *   <li>{@code GET /blog/rss.xml} — {@code blog_rss}, {@code _format=xml}.</li>
 * </ul>
 * Symfony chose the template by format ({@code blog/index.<_format>.twig}); here the html routes
 * render the {@code blog/index} view and the xml route renders the {@code blog/rss} view with an
 * {@code application/xml} content type. The Thymeleaf templates themselves are task 8.3, so the
 * referenced views may not exist yet.
 *
 * <p><b>Pagination parity:</b> the PHP action delegated to {@code PostRepository::findLatest(page,
 * tag)}, which returns the Symfony Demo {@code Paginator} (page size 10). Here we query
 * {@link PostRepository#findLatest(Tag, org.springframework.data.domain.Pageable)} for the
 * requested page and wrap the result in the {@link Paginator} service so the view sees the same
 * metadata surface ({@code results}, {@code currentPage}, {@code lastPage}, {@code hasToPaginate},
 * {@code hasPreviousPage}/{@code previousPage}, {@code hasNextPage}/{@code nextPage}) the Twig
 * template consumed.
 */
@Controller
@RequestMapping("/blog")
// Not final: the @PreAuthorize on commentNew requires Spring to create a CGLIB proxy of this
// controller (method security), which cannot subclass a final class.
public class BlogController {

    private final PostRepository posts;
    private final TagRepository tags;
    private final CommentRepository comments;
    private final MarkdownRenderer markdownRenderer;
    private final ApplicationEventPublisher eventPublisher;

    public BlogController(PostRepository posts, TagRepository tags, CommentRepository comments,
                          MarkdownRenderer markdownRenderer, ApplicationEventPublisher eventPublisher) {
        this.posts = posts;
        this.tags = tags;
        this.comments = comments;
        this.markdownRenderer = markdownRenderer;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Blog index, page 1 — {@code GET /blog/} ({@code blog_index}). Mirrors the PHP
     * {@code index()} html format with {@code page=1}.
     *
     * @param tag  optional tag name from the {@code ?tag=} query parameter
     * @param model the view model
     * @return the {@code blog/index} view name
     */
    @GetMapping(value = "/")
    public String index(@RequestParam(name = "tag", required = false) String tag, Model model) {
        return renderIndex(1, tag, model);
    }

    /**
     * Paginated blog index — {@code GET /blog/page/{page}} ({@code blog_index_paginated}).
     * {@code page} must be a positive integer, mirroring the PHP
     * {@code requirements: ['page' => Requirement::POSITIVE_INT]}.
     *
     * @param page  the requested 1-based page number (positive integer)
     * @param tag   optional tag name from the {@code ?tag=} query parameter
     * @param model the view model
     * @return the {@code blog/index} view name
     */
    @GetMapping(value = "/page/{page:[1-9]\\d*}")
    public String indexPaginated(@PathVariable("page") int page,
                                 @RequestParam(name = "tag", required = false) String tag,
                                 Model model) {
        return renderIndex(page, tag, model);
    }

    /**
     * RSS feed — {@code GET /blog/rss.xml} ({@code blog_rss}). Same data as the index (page 1,
     * optional tag filter) rendered as XML. Mirrors the PHP {@code _format=xml} branch.
     *
     * @param tag   optional tag name from the {@code ?tag=} query parameter
     * @param model the view model
     * @return the {@code blog/rss} view name (rendered as {@code application/xml})
     */
    @GetMapping(value = "/rss.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String rss(@RequestParam(name = "tag", required = false) String tag, Model model) {
        populateModel(1, tag, model);
        return "blog/rss";
    }

    /**
     * Post detail — {@code GET /blog/posts/{slug}} ({@code blog_post}). Port of the PHP
     * {@code postShow(Post $post)} action.
     *
     * <p>The PHP action relied on Symfony's {@code EntityValueResolver} to look up the {@link Post}
     * by its {@code slug} route parameter and return a 404 automatically when no post matched. Here
     * the lookup is explicit via {@link PostRepository#findBySlug(String)}, throwing
     * {@link ResponseStatusException} with {@link HttpStatus#NOT_FOUND} when absent
     * (Requirement 4.2).
     *
     * <p>The {@code slug} path segment is constrained to Symfony's {@code Requirement::ASCII_SLUG}
     * pattern ({@code [a-z0-9]+(?:-[a-z0-9]+)*}) so non-slug paths fall through to other routes
     * exactly as in the PHP app.
     *
     * <p>The post's Markdown {@code content} is rendered to HTML via {@link MarkdownRenderer}
     * (Requirement 4.3) and exposed as {@code contentHtml}; the PHP Twig template applied the
     * {@code markdown_to_html} filter at render time. The post itself is exposed so the template can
     * read its metadata and iterate {@code post.getComments()} (already ordered newest first by the
     * entity's {@code @OrderBy("publishedAt DESC")}, Requirement 4.1).
     *
     * @param slug  the ASCII slug identifying the post
     * @param model the view model
     * @return the {@code blog/post_show} view name
     */
    @GetMapping(value = "/posts/{slug:[a-z0-9]+(?:-[a-z0-9]+)*}")
    public String postShow(@PathVariable("slug") String slug, Model model) {
        Post post = posts.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("post", post);
        model.addAttribute("contentHtml", markdownRenderer.toHtml(post.getContent()));
        return "blog/post_show";
    }

    /**
     * Comment creation — {@code POST /blog/comment/{postSlug}/new} ({@code comment_new}). Port of
     * the PHP {@code commentNew()} action (Requirements 5.1, 5.2, 5.3).
     *
     * <p>Parity notes with the PHP action:
     * <ul>
     *   <li><b>Authentication:</b> PHP guarded with {@code #[IsGranted('IS_AUTHENTICATED')]}; here
     *       {@code @PreAuthorize("isAuthenticated()")} enforces the same (the {@code /blog/**}
     *       path is otherwise {@code permitAll} in {@link com.symfony.demo.config.SecurityConfig}).
     *       Anonymous requests are rejected by Spring Security (redirect to {@code /login} via the
     *       form-login entry point), matching Symfony's firewall behavior.</li>
     *   <li><b>Post lookup:</b> the {@code postSlug} maps to {@link Post#getSlug()} via
     *       {@link PostRepository#findBySlug(String)}; a missing post yields
     *       {@link HttpStatus#NOT_FOUND} through {@link ResponseStatusException}, mirroring the
     *       PHP {@code #[MapEntity(mapping: ['postSlug' => 'slug'])]} 404 behavior and matching the
     *       {@link #postShow} pattern. The {@code postSlug} segment is constrained to Symfony's
     *       {@code Requirement::ASCII_SLUG} ({@code [a-z0-9]+(?:-[a-z0-9]+)*}).</li>
     *   <li><b>Binding + validation:</b> the submitted {@code content} field binds to the reused
     *       {@link CommentDto} ({@code com.symfony.demo.form.CommentDto}), whose constraints port
     *       the PHP {@code Comment} entity's {@code @NotBlank}/{@code @Length}/{@code @Assert\IsTrue}
     *       (spam) rules.</li>
     *   <li><b>Success:</b> a new {@link Comment} is created with the current {@link User} as author
     *       and attached to the post (its {@code publishedAt} is set to now by the entity
     *       constructor, mirroring the PHP {@code Comment} constructor). It is persisted
     *       ({@link CommentRepository#save}), a {@link CommentCreatedEvent} is published
     *       (task 9.2 adds the listener), and the response is a <b>303 SEE_OTHER</b> redirect to
     *       {@code /blog/posts/{slug}} — matching {@code redirectToRoute('blog_post', ...,
     *       Response::HTTP_SEE_OTHER)}.</li>
     *   <li><b>Validation failure:</b> the {@code blog/comment_form_error} view is re-rendered with
     *       the post and bound DTO (carrying field errors), <em>not</em> a redirect — mirroring the
     *       PHP {@code render('blog/comment_form_error.html.twig', ...)}, which returns HTTP 200.</li>
     * </ul>
     *
     * @param slug     the ASCII slug of the post being commented on ({@code postSlug} route var)
     * @param comment  the bound comment form DTO (field {@code content})
     * @param binding  the binding/validation result for {@code comment}
     * @param principal the authenticated user (never {@code null} thanks to {@code @PreAuthorize})
     * @param model    the view model (populated on validation failure)
     * @return a 303 redirect to the post page on success, or the error view (HTTP 200) on failure
     */
    @PostMapping(value = "/comment/{postSlug:[a-z0-9]+(?:-[a-z0-9]+)*}/new")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView commentNew(@PathVariable("postSlug") String slug,
                                   @Valid @ModelAttribute("comment") CommentDto comment,
                                   BindingResult binding,
                                   @AuthenticationPrincipal SecurityUser principal,
                                   Model model) {
        Post post = posts.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (binding.hasErrors()) {
            // Parity with PHP: re-render the comment error form (HTTP 200) instead of redirecting.
            // The bound `comment` DTO and its BindingResult are already in the implicit model
            // (via @ModelAttribute + @Valid); only the post needs adding for the view.
            model.addAttribute("post", post);
            return new ModelAndView("blog/comment_form_error");
        }

        Comment entity = new Comment();
        entity.setAuthor(principal.getDomainUser());
        entity.setContent(comment.getContent());
        post.addComment(entity);

        comments.save(entity);
        eventPublisher.publishEvent(new CommentCreatedEvent(entity));

        // HTTP 303 SEE_OTHER to blog_post (Response::HTTP_SEE_OTHER in the PHP source).
        RedirectView redirect = new RedirectView("/blog/posts/" + post.getSlug());
        redirect.setStatusCode(HttpStatus.SEE_OTHER);
        return new ModelAndView(redirect);
    }

    /**
     * Search — {@code GET /blog/search} ({@code blog_search}). Port of the PHP {@code search()}
     * action combined with the {@code BlogSearchComponent} live component that the PHP
     * {@code blog/search.html.twig} template embedded.
     *
     * <p>The PHP {@code search()} action itself only rendered the search page with the {@code query}
     * string; the actual matching posts were produced by the {@code blog_search} live component,
     * whose {@code getPosts()} delegated to {@code PostRepository::findBySearchQuery($query)}. Since
     * Spring has no live-component equivalent, that logic is folded into this handler: the
     * {@code ?q=} value is exposed as {@code query} (defaulting to an empty string, matching the PHP
     * {@code (string) $request->query->get('q', '')}) and the matching posts are exposed as
     * {@code posts} (Requirements 6.1, 6.2). A missing or term-less query yields an empty result
     * list, mirroring {@code findBySearchQuery} returning {@code []}.
     *
     * @param query the {@code ?q=} search string (optional; defaults to empty)
     * @param model the view model
     * @return the {@code blog/search} view name
     */
    @GetMapping(value = "/search")
    public String search(@RequestParam(name = "q", required = false, defaultValue = "") String query,
                         Model model) {
        model.addAttribute("query", query);
        model.addAttribute("posts", posts.findBySearchQuery(query));
        return "blog/search";
    }

    /**
     * Shared html handler: builds the paginator + tag model and returns the index view.
     */
    private String renderIndex(int page, String tagName, Model model) {
        populateModel(page, tagName, model);
        return "blog/index";
    }

    /**
     * Resolves the optional tag, queries the latest posts for {@code page}, and exposes the
     * {@code paginator} and {@code tagName} model attributes the view expects (parity with the PHP
     * {@code ['paginator' => $latestPosts, 'tagName' => $tag?->getName()]}).
     */
    private void populateModel(int page, String tagName, Model model) {
        // PHP: only look up a tag when the `tag` query parameter is present. A present-but-unknown
        // tag resolves to null (findOneBy returns null), leaving tagName null and applying no
        // filter — matching the PHP behaviour.
        Tag tag = null;
        if (tagName != null) {
            tag = tags.findByName(tagName).orElse(null);
        }

        Paginator<Post> paginator = buildPaginator(page, tag);

        model.addAttribute("paginator", paginator);
        model.addAttribute("tagName", tag != null ? tag.getName() : null);
    }

    /**
     * Fetches the requested page of latest posts from the repository and wraps it in the
     * {@link Paginator} service (page size {@link Paginator#PAGE_SIZE} = 10). The repository owns
     * ordering ({@code publishedAt DESC}) and the published-at cutoff; the {@link Paginator}
     * reproduces the PHP metadata arithmetic from the total count and current page.
     */
    private Paginator<Post> buildPaginator(int page, Tag tag) {
        int pageSize = Paginator.PAGE_SIZE;
        int pageNumber = Math.max(1, page) - 1;

        Page<Post> pageResult = posts.findLatest(tag, PageRequest.of(pageNumber, pageSize));
        long total = pageResult.getTotalElements();
        List<Post> content = pageResult.getContent();

        // Adapt the already-fetched Spring Data page to the Paginator's PageSource. The Paginator
        // recomputes the first-result offset internally but we return the pre-fetched slice, so no
        // second query is issued; count() feeds the total for the metadata arithmetic.
        Paginator.PageSource<Post> source = new Paginator.PageSource<>() {
            @Override
            public long count() {
                return total;
            }

            @Override
            public List<Post> slice(int firstResult, int maxResults) {
                return content;
            }
        };

        return new Paginator<Post>(pageSize).paginate(source, page);
    }
}
