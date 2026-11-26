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

/** PUT /api/me/password through the whole stack with a real PostgreSQL. Each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ChangePasswordTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private long aliceId;
    private String phoneAccess;
    private String phoneRefresh;
    private String laptopRefresh;

    @BeforeEach
    void registerAliceOnTwoDevices() throws Exception {
        for (String name : new String[] {"alice01", "bob01"}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                    {"firstName": "X", "lastName": "Nguyen", "userName": "%s",
                     "email": "%s@example.com", "password": "oldpass123", "timeZone": "UTC"}
                    """.formatted(name, name))).andExpect(status().isCreated());
        }
        String phone = login("alice01", "oldpass123").andReturn().getResponse().getContentAsString();
        String laptop = login("alice01", "oldpass123").andReturn().getResponse().getContentAsString();
        aliceId = ((Number) JsonPath.read(phone, "$.member.id")).longValue();
        phoneAccess = JsonPath.read(phone, "$.accessToken");
        phoneRefresh = JsonPath.read(phone, "$.refreshToken");
        laptopRefresh = JsonPath.read(laptop, "$.refreshToken");
    }

    private ResultActions login(String user, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\": \"" + user + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions change(String accessToken, String current, String next) throws Exception {
        var request = put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}");
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return mvc.perform(request);
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + token + "\"}"));
    }

    private long revokedTokensOf(long memberId) {
        return jdbc.queryForObject(
                "select count(*) from refresh_token where member_id = ? and revoked_at is not null", Long.class,
                memberId);
    }

    @Test
    void theNewPasswordWorksAndTheOldOneDoesNot() throws Exception {
        change(phoneAccess, "oldpass123", "newpass456").andExpect(status().isOk());

        login("alice01", "newpass456").andExpect(status().isOk());
        login("alice01", "oldpass123").andExpect(status().isUnauthorized());
    }

    @Test
    void returnsANewSessionForThisDeviceInTheLoginFormat() throws Exception {
        String body = change(phoneAccess, "oldpass123", "newpass456")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.member.userName").value("alice01"))
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(body, "$.accessToken");
        String newRefresh = JsonPath.read(body, "$.refreshToken");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());
        refresh(newRefresh).andExpect(status().isOk());
    }

    @Test
    void everyOldRefreshTokenIsRevoked() throws Exception {
        change(phoneAccess, "oldpass123", "newpass456").andExpect(status().isOk());

        refresh(phoneRefresh).andExpect(status().isUnauthorized());
        refresh(laptopRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotAffectAnotherMember() throws Exception {
        String bob = login("bob01", "oldpass123").andReturn().getResponse().getContentAsString();
        long bobId = ((Number) JsonPath.read(bob, "$.member.id")).longValue();

        change(phoneAccess, "oldpass123", "newpass456").andExpect(status().isOk());

        assertThat(revokedTokensOf(bobId)).isZero();
        login("bob01", "oldpass123").andExpect(status().isOk());
    }

    @Test
    void recordsTheCallerInTheAuditColumns() throws Exception {
        change(phoneAccess, "oldpass123", "newpass456").andExpect(status().isOk());
        em.flush();

        Number updatedBy = jdbc.queryForObject("select updated_by from person where person_id = ?", Number.class,
                aliceId);
        assertThat(updatedBy.longValue()).isEqualTo(aliceId);
    }

    @Test
    void wrongCurrentPasswordChangesNothing() throws Exception {
        change(phoneAccess, "wrong-password", "newpass456").andExpect(status().isUnauthorized());

        login("alice01", "oldpass123").andExpect(status().isOk());
        assertThat(revokedTokensOf(aliceId)).isZero();
    }

    @Test
    void theNewPasswordMustDiffer() throws Exception {
        change(phoneAccess, "oldpass123", "oldpass123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The new password must be different from the current password"));

        assertThat(revokedTokensOf(aliceId)).isZero();
    }

    @Test
    void rejectsAWeakOrMissingNewPassword() throws Exception {
        change(phoneAccess, "oldpass123", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        mvc.perform(put("/api/me/password").header("Authorization", "Bearer " + phoneAccess)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\": \"oldpass123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsAnAccessToken() throws Exception {
        change(null, "oldpass123", "newpass456").andExpect(status().isUnauthorized());
        change("garbage", "oldpass123", "newpass456").andExpect(status().isUnauthorized());

        login("alice01", "oldpass123").andExpect(status().isOk());
    }

    @Test
    void aValidTokenOfADeletedMemberIsRejected() throws Exception {
        jdbc.update("update person set exclusion_cd = 'EX' where person_id = ?", aliceId);
        em.clear();

        change(phoneAccess, "oldpass123", "newpass456").andExpect(status().isUnauthorized());
    }
}
