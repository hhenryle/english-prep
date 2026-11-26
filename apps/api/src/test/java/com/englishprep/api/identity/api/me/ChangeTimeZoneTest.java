package com.englishprep.api.identity.api.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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

/** PUT /api/me/time-zone through the whole stack with a real PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ChangeTimeZoneTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long aliceId;
    private String access;
    private String refresh;

    @BeforeEach
    void registerAndLogIn() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "alice01",
                 "email": "alice@example.com", "password": "secret123", "timeZone": "UTC"}
                """)).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"alice01\", \"password\": \"secret123\"}"))
                .andReturn().getResponse().getContentAsString();
        aliceId = ((Number) JsonPath.read(body, "$.member.id")).longValue();
        access = JsonPath.read(body, "$.accessToken");
        refresh = JsonPath.read(body, "$.refreshToken");
    }

    private ResultActions change(String accessToken, String json) throws Exception {
        var request = put("/api/me/time-zone").contentType(MediaType.APPLICATION_JSON).content(json);
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return mvc.perform(request);
    }

    private static String zone(String value) {
        return "{\"timeZone\": \"" + value + "\"}";
    }

    @Test
    void changesTheZoneAndReturnsTheUpdatedAccount() throws Exception {
        change(access, zone("Asia/Ho_Chi_Minh"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.userName").value("alice01"));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));
    }

    @Test
    void sessionsStayValid() throws Exception {
        change(access, zone("Europe/Paris")).andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refresh + "\"}")).andExpect(status().isOk());
    }

    @Test
    void recordsTheCallerInTheAuditColumns() throws Exception {
        change(access, zone("Europe/Paris")).andExpect(status().isOk());
        em.flush();

        Number updatedBy = jdbc.queryForObject("select updated_by from person where person_id = ?", Number.class,
                aliceId);
        assertThat(updatedBy.longValue()).isEqualTo(aliceId);
    }

    @Test
    void rejectsAnInvalidBlankOrMissingZone() throws Exception {
        change(access, zone("Mars/Olympus")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timeZone").exists());
        change(access, zone("  ")).andExpect(status().isBadRequest());
        change(access, "{}").andExpect(status().isBadRequest());
        change(access, "{\"timeZone\": null}").andExpect(status().isBadRequest());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.timeZone").value("UTC"));
    }

    @Test
    void needsAnAccessToken() throws Exception {
        change(null, zone("Europe/Paris")).andExpect(status().isUnauthorized());
        change("garbage", zone("Europe/Paris")).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenOfADeletedMemberIsRejected() throws Exception {
        jdbc.update("update person set exclusion_cd = 'EX' where person_id = ?", aliceId);
        em.clear();

        change(access, zone("Europe/Paris")).andExpect(status().isUnauthorized());
    }
}
