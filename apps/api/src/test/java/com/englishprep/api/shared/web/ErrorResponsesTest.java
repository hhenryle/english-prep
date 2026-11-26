package com.englishprep.api.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.application.token.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every error must be an RFC 9457 problem document ({@code application/problem+json}), whatever
 * caused it, and must not reveal internals. Two test-only endpoints produce the errors that no real
 * endpoint can produce yet (an unexpected exception, and "logged in but not allowed").
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ErrorResponsesTest.TestEndpoints.class})
class ErrorResponsesTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEndpoints {

        @Bean
        ThrowingController throwingController() {
            return new ThrowingController();
        }
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("not allowed");
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService accessTokens;

    private ResultActions getWithToken(String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + accessTokens.issue(1L).value()));
    }

    private static org.springframework.test.web.servlet.ResultMatcher problem() {
        return content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(problem());
    }

    @Test
    void wrongMethodIs405() throws Exception {
        getWithToken("/api/auth/login").andExpect(status().isMethodNotAllowed()).andExpect(problem());
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(problem());
    }

    @Test
    void unknownPathIs404ForALoggedInCaller() throws Exception {
        getWithToken("/api/does-not-exist").andExpect(status().isNotFound()).andExpect(problem());
    }

    @Test
    void unknownPathIs401ForAnAnonymousCaller() throws Exception {
        // Not 404: an anonymous caller cannot tell which paths exist.
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized()).andExpect(problem());
    }

    @Test
    void anUnexpectedExceptionIs500WithoutRevealingIt() throws Exception {
        String body = getWithToken("/test/boom")
                .andExpect(status().isInternalServerError())
                .andExpect(problem())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret internal detail").doesNotContain("IllegalStateException");
    }

    @Test
    void accessDeniedIs403() throws Exception {
        getWithToken("/test/forbidden")
                .andExpect(status().isForbidden())
                .andExpect(problem())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }
}
