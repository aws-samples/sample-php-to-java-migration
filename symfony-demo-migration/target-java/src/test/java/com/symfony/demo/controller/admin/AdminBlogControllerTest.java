package com.symfony.demo.controller.admin;

import com.symfony.demo.config.SecurityConfig;
import com.symfony.demo.entity.Post;
import com.symfony.demo.entity.User;
import com.symfony.demo.repository.PostRepository;
import com.symfony.demo.repository.TagRepository;
import com.symfony.demo.security.PostPermissionEvaluator;
import com.symfony.demo.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice test for {@link AdminBlogController} (task 11.1). Verifies the parity behaviors of
 * the PHP {@code Admin\BlogController}: the admin lists their own posts, a non-admin is forbidden,
 * a valid new post persists and 303-redirects, editing another author's post is forbidden, and
 * delete removes the post (clearing tags) and 303-redirects.
 *
 * <p>Uses a web slice with {@link SecurityConfig} imported so class-level {@code @PreAuthorize}
 * method security, the role hierarchy, and Spring's CSRF protection are all active — matching the
 * runtime configuration.
 */
@WebMvcTest(AdminBlogController.class)
@Import(SecurityConfig.class)
class AdminBlogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostRepository posts;
    @MockitoBean
    private TagRepository tags;
    @MockitoBean
    private PostPermissionEvaluator permissionEvaluator;

    private Authentication admin() {
        User user = new User();
        user.setUsername("jane_admin");
        user.setFullName("Jane Admin");
        user.setPassword("x");
        user.setRoles(List.of(User.ROLE_ADMIN));
        SecurityUser principal = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities());
    }

    private Authentication regularUser() {
        User user = new User();
        user.setUsername("john_user");
        user.setFullName("John User");
        user.setPassword("x");
        user.setRoles(List.of(User.ROLE_USER));
        SecurityUser principal = new SecurityUser(user);
        return new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities());
    }

    private Post samplePost() {
        Post post = new Post();
        post.setTitle("Sample");
        post.setSlug("sample");
        post.setSummary("Summary");
        post.setContent("Some content here");
        return post;
    }

    @Test
    void adminListsOwnPosts() throws Exception {
        when(posts.findByAuthorOrderByPublishedAtDesc(any(User.class)))
                .thenReturn(List.of(samplePost()));

        // The admin/blog/index Thymeleaf template is authored in task 11.2, so the view now
        // renders: assert the admin is authorized (200 OK), the expected view is selected, and the
        // current user's posts are exposed as the `posts` model attribute.
        mockMvc.perform(get("/admin/post/").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/blog/index"))
                .andExpect(model().attributeExists("posts"));

        verify(posts, times(1)).findByAuthorOrderByPublishedAtDesc(any(User.class));
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/post/").with(authentication(regularUser())))
                .andExpect(status().isForbidden());

        verify(posts, never()).findByAuthorOrderByPublishedAtDesc(any());
    }

    @Test
    void newPostPersistsAndRedirects303() throws Exception {
        when(posts.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/admin/post/new")
                        .param("title", "My New Post")
                        .param("summary", "A short summary")
                        .param("content", "Long enough content body")
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/post/"));

        verify(posts, times(1)).save(any(Post.class));
    }

    @Test
    void newPostSaveAndCreateNewRedirectsToNew() throws Exception {
        when(posts.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/admin/post/new")
                        .param("title", "Another Post")
                        .param("summary", "A short summary")
                        .param("content", "Long enough content body")
                        .param("saveAndCreateNew", "")
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/post/new"));
    }

    @Test
    void editOfAnotherAuthorsPostIsForbidden() throws Exception {
        when(posts.findById(5)).thenReturn(Optional.of(samplePost()));
        // Voter denies: current user is not the author.
        when(permissionEvaluator.hasPermission(any(Authentication.class), any(), eq("edit")))
                .thenReturn(false);

        mockMvc.perform(post("/admin/post/5/edit")
                        .param("title", "Hacked")
                        .param("summary", "s")
                        .param("content", "content long enough")
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(posts, never()).save(any());
    }

    @Test
    void deleteRemovesPostAndRedirects303() throws Exception {
        when(posts.findById(7)).thenReturn(Optional.of(samplePost()));
        when(permissionEvaluator.hasPermission(any(Authentication.class), any(), eq("delete")))
                .thenReturn(true);

        mockMvc.perform(post("/admin/post/7/delete")
                        .with(authentication(admin()))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/post/"));

        verify(posts, times(1)).delete(any(Post.class));
    }
}
