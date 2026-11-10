package com.englishprep.api.identity.api.me;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.application.token.AccessTokenService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** GET /api/me through the whole stack with a real PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService accessTokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private void register(String userName, String firstName) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "%s", "lastName": "Nguyen", "userName": "%s",
                 "email": "%s@example.com", "password": "secret123", "timeZone": "Asia/Ho_Chi_Minh"}
                """.formatted(firstName, userName, userName))).andExpect(status().isCreated());
    }

    /** Logs in and returns the access token. */
    private String accessTokenOf(String userName) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"" + userName + "\", \"password\": \"secret123\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void returnsTheAccountOfTheTokenOwnerWithoutThePassword() throws Exception {
        register("alice01", "Alice");
        String token = accessTokenOf("alice01");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.lastName").value("Nguyen"))
                .andExpect(jsonPath("$.userName").value("alice01"))
                .andExpect(jsonPath("$.email").value("alice01@example.com"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void eachTokenGivesItsOwnerAndNeverSomeoneElse() throws Exception {
        register("alice01", "Alice");
        register("bob01", "Bob");
        String aliceToken = accessTokenOf("alice01");
        String bobToken = accessTokenOf("bob01");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(jsonPath("$.userName").value("alice01"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + bobToken))
                .andExpect(jsonPath("$.userName").value("bob01"));
    }

    @Test
    void theResponseIsNeverCached() throws Exception {
        register("alice01", "Alice");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + accessTokenOf("alice01")))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void withoutATokenItIs401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenOfADeletedMemberIs401() throws Exception {
        register("alice01", "Alice");
        String token = accessTokenOf("alice01");
        jdbc.update("update person set exclusion_cd = 'EX' where user_nm = 'alice01'");
        em.clear(); // forget the member that login loaded earlier in this same transaction

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    @Test
    void aValidTokenOfAMemberThatNeverExistedIs401() throws Exception {
        String token = accessTokens.issue(999_999L).value();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
