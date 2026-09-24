package com.symfony.demo.controller;

import com.symfony.demo.config.SecurityConfig;
import com.symfony.demo.security.PostPermissionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice test for {@link SecurityController#login} (task 10.1). Verifies parity with the PHP
 * {@code SecurityController::login()}: anonymous users see the login view (with the
 * {@code last_username}/{@code error} model attributes), and already-authenticated users are
 * redirected to the blog index.
 *
 * <p>{@link SecurityConfig} is imported so the login page is reachable ({@code permitAll}) exactly
 * as at runtime.
 */
@WebMvcTest(SecurityController.class)
@Import(SecurityConfig.class)
class SecurityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostPermissionEvaluator postPermissionEvaluator;

    private Authentication authenticatedUser() {
        return new UsernamePasswordAuthenticationToken(
                "john_user", "x", AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    @Test
    void anonymousGetRendersLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("security/login"))
                // last_username is always present (empty on a fresh visit); `error` is only added
                // when a prior login failed, so it is absent here — the template treats absent and
                // null identically via th:if.
                .andExpect(model().attributeExists("last_username"));
    }

    @Test
    void authenticatedGetRedirectsToBlogIndex() throws Exception {
        mockMvc.perform(get("/login").with(authentication(authenticatedUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/blog/"));
    }
}
