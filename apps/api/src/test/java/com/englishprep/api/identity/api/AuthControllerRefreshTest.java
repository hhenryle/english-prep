package com.englishprep.api.identity.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calls POST /api/auth/refresh through the whole stack. Rolled back after each test, so it cannot show
 * what is committed: see RefreshTokenReuseTest for reuse detection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerRefreshTest {

    @Autowired
    private MockMvc mvc;

    private String refreshToken;

    @BeforeEach
    void registerAndLogIn() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "alice01",
                 "email": "alice@example.com", "password": "secret123", "timeZone": "UTC"}
                """)).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"alice01\", \"password\": \"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        refreshToken = JsonPath.read(body, "$.refreshToken");
    }

    private ResultActions refresh(String token) throws Exception {
        String json = token == null ? "{}" : "{\"refreshToken\": \"" + token + "\"}";
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void returnsANewPairOfTokensInTheLoginFormat() throws Exception {
        refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").exists())
                .andExpect(jsonPath("$.member.userName").value("alice01"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void theNewRefreshTokenIsDifferentAndTheNewAccessTokenWorks() throws Exception {
        String body = refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(body, "$.refreshToken");
        String newAccess = JsonPath.read(body, "$.accessToken");

        org.assertj.core.api.Assertions.assertThat(newRefresh).isNotEqualTo(refreshToken);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("alice01"));
    }

    @Test
    void theOldTokenCannotBeUsedAgain() throws Exception {
        refresh(refreshToken).andExpect(status().isOk());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
    }

    @Test
    void rejectsAGarbageToken() throws Exception {
        refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired refresh token"));
    }

    @Test
    void rejectsAMissingOrBlankToken() throws Exception {
        refresh(null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.refreshToken").exists());
        refresh("  ").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnOverlongToken() throws Exception {
        refresh("x".repeat(201)).andExpect(status().isBadRequest());
    }

    @Test
    void anAccessTokenIsNotNeeded() throws Exception {
        // an expired or wrong access token on this public path must not block the refresh
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk());
    }
}
