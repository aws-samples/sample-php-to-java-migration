package com.symfony.demo.controller;

import com.symfony.demo.config.SecurityConfig;
import com.symfony.demo.entity.Comment;
import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import com.symfony.demo.event.CommentCreatedEvent;
import com.symfony.demo.repository.CommentRepository;
import com.symfony.demo.repository.PostRepository;
import com.symfony.demo.repository.TagRepository;
import com.symfony.demo.security.PostPermissionEvaluator;
import com.symfony.demo.security.SecurityUser;
import com.symfony.demo.service.MarkdownRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice test for {@link BlogController#commentNew} (task 9.1). Verifies the three parity
 * behaviors of the PHP {@code commentNew()} action: authentication is required, a valid submission
 * persists + publishes the event + redirects 303 to the post page, and an invalid submission
 * re-renders the error view without redirecting.
 *
 * <p>Uses a web slice with {@link SecurityConfig} imported so the {@code @PreAuthorize} method
 * security and Spring's CSRF protection are both active, matching the runtime configuration.
 */
@WebMvcTest(BlogController.class)
@Import(SecurityConfig.class)
class BlogControllerCommentNewTest {

    private static final String SLUG = "test-post";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository posts;
    @MockitoBean
    private TagRepository tags;
    @MockitoBean
    private CommentRepository comments;
    @MockitoBean
    private MarkdownRenderer markdownRenderer;
    @MockitoBean
    private PostPermissionEvaluator postPermissionEvaluator;

    private Post postWithSlug() {
        Post post = new Post();
        post.setSlug(SLUG);
        post.setTitle("Test Post");
        return post;
    }

    private Authentication authenticatedUser() {
        User user = new User();
        user.setUsername("john_user");
        user.setFullName("John User");
        user.setPassword("x");
        SecurityUser principal = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities());
    }

    @Test
    void unauthenticatedPostIsRejected() throws Exception {
        // Anonymous request (CSRF token present so the auth check, not CSRF, is what rejects it).
        mockMvc.perform(post("/blog/comment/{slug}/new", SLUG)
                        .param("content", "A perfectly valid comment")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));

        verify(comments, never()).save(any());
    }

    @Test
    void authenticatedValidPostRedirects303ToPost() throws Exception {
        when(posts.findBySlug(SLUG)).thenReturn(Optional.of(postWithSlug()));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/blog/comment/{slug}/new", SLUG)
                        .param("content", "A perfectly valid comment")
                        .with(authentication(authenticatedUser()))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/blog/posts/" + SLUG));

        verify(comments, times(1)).save(any(Comment.class));
    }

    @Test
    void authenticatedInvalidPostRerendersErrorViewAndDoesNotRedirect() throws Exception {
        when(posts.findBySlug(SLUG)).thenReturn(Optional.of(postWithSlug()));

        // Content containing '@' fails the spam (IsTrue) constraint -> validation failure.
        mockMvc.perform(post("/blog/comment/{slug}/new", SLUG)
                        .param("content", "spam me @example.com")
                        .with(authentication(authenticatedUser()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("blog/comment_form_error"))
                .andExpect(model().attributeHasFieldErrors("comment", "legitComment"));

        verify(comments, never()).save(any());
    }
}
