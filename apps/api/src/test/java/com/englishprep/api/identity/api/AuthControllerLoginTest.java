package com.englishprep.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.application.token.AccessTokenService;
import com.englishprep.api.identity.application.token.RefreshTokenService;
import com.englishprep.api.identity.domain.token.RefreshToken;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calls POST /api/auth/login through the whole stack with a real PostgreSQL. Each test starts
 * from an account created through the register endpoint, and is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerLoginTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService accessTokens;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void registerAlice() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "alice01",
                 "email": "alice@example.com", "password": "secret123", "timeZone": "Asia/Ho_Chi_Minh"}
                """)).andExpect(status().isCreated());
    }

    private ResultActions login(String login, String password) throws Exception {
        String json = """
                {"login": %s, "password": %s}
                """.formatted(quote(login), quote(password));
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    @Test
    void logsInWithTheUserNameAndReturnsTokensAndMember() throws Exception {
        login("alice01", "secret123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.accessTokenExpiresAt").exists())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").exists())
                .andExpect(jsonPath("$.member.id").isNumber())
                .andExpect(jsonPath("$.member.userName").value("alice01"))
                .andExpect(jsonPath("$.member.email").value("alice@example.com"))
                .andExpect(jsonPath("$.member.timeZone").value("Asia/Ho_Chi_Minh"));
    }

    @Test
    void logsInWithTheEmailIgnoringCase() throws Exception {
        login("  Alice@Example.COM ", "secret123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.userName").value("alice01"));
    }

    @Test
    void theAccessTokenIsValidForTheLoggedInMember() throws Exception {
        String body = login("alice01", "secret123").andReturn().getResponse().getContentAsString();

        long memberId = ((Number) JsonPath.read(body, "$.member.id")).longValue();
        String accessToken = JsonPath.read(body, "$.accessToken");

        assertThat(accessTokens.verify(accessToken)).contains(memberId);
    }

    @Test
    void theRefreshTokenIsStoredOnlyAsAHash() throws Exception {
        String body = login("alice01", "secret123").andReturn().getResponse().getContentAsString();

        long memberId = ((Number) JsonPath.read(body, "$.member.id")).longValue();
        String refreshToken = JsonPath.read(body, "$.refreshToken");

        assertThat(refreshTokens.findActive(refreshToken)).get().extracting(RefreshToken::memberId).isEqualTo(memberId);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Long.class,
                refreshToken)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where member_id = ?", Long.class,
                memberId)).isEqualTo(1L);
    }

    @Test
    void eachLoginGetsItsOwnTokens() throws Exception {
        String first = login("alice01", "secret123").andReturn().getResponse().getContentAsString();
        String second = login("alice01", "secret123").andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(first, "$.refreshToken"))
                .isNotEqualTo((String) JsonPath.read(second, "$.refreshToken"));
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Long.class)).isEqualTo(2L);
    }

    @Test
    void wrongPasswordAndUnknownAccountGiveTheSame401() throws Exception {
        String wrongPassword = login("alice01", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid login or password"))
                .andReturn().getResponse().getContentAsString();
        String unknownAccount = login("nobody", "secret123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownAccount).isEqualTo(wrongPassword);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Long.class)).isZero();
    }

    @Test
    void missingFieldsGive400WithTheFieldNames() throws Exception {
        login(null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.login").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        login("   ", "secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.login").exists());
    }

    @Test
    void theResponseIsNeverCachedAndHasNoPassword() throws Exception {
        login("alice01", "secret123")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.member.passwordHash").doesNotExist());
    }
}
