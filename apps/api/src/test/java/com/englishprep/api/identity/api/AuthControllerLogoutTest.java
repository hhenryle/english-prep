package com.englishprep.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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

/** POST /api/auth/logout and /logout-all through the whole stack. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerLogoutTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Session alicePhone;
    private Session aliceLaptop;
    private Session bob;

    /** What a login returns that the tests need. */
    private record Session(long memberId, String accessToken, String refreshToken) {
    }

    @BeforeEach
    void createMembersAndSessions() throws Exception {
        register("alice01");
        register("bob01");
        alicePhone = login("alice01");
        aliceLaptop = login("alice01");
        bob = login("bob01");
    }

    private void register(String userName) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "X", "lastName": "Nguyen", "userName": "%s",
                 "email": "%s@example.com", "password": "secret123", "timeZone": "UTC"}
                """.formatted(userName, userName))).andExpect(status().isCreated());
    }

    private Session login(String userName) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"" + userName + "\", \"password\": \"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.member.id");
        return new Session(id.longValue(), JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private ResultActions logout(String accessToken, String refreshToken) throws Exception {
        var request = post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": " + (refreshToken == null ? "null" : "\"" + refreshToken + "\"") + "}");
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return mvc.perform(request);
    }

    /** The refresh_token rows of a member, read straight from the table: revoked? and who changed it. */
    private List<Map<String, Object>> rowsOf(Session session) {
        return jdbc.queryForList(
                "select revoked_at is not null as revoked, updated_by from refresh_token where member_id = ?",
                session.memberId());
    }

    private long revokedCount(Session session) {
        return rowsOf(session).stream().filter(r -> (Boolean) r.get("revoked")).count();
    }

    @Test
    void logoutRevokesOnlyTheGivenSessionAndRecordsWhoDidIt() throws Exception {
        logout(alicePhone.accessToken(), alicePhone.refreshToken()).andExpect(status().isNoContent());

        assertThat(revokedCount(alicePhone)).isEqualTo(1);
        // the row that was revoked now says that Alice herself changed it
        assertThat(rowsOf(alicePhone)).filteredOn(r -> (Boolean) r.get("revoked"))
                .extracting(r -> ((Number) r.get("updated_by")).longValue()).containsExactly(alicePhone.memberId());
    }

    @Test
    void theLoggedOutTokenCanNoLongerBeRefreshed() throws Exception {
        logout(alicePhone.accessToken(), alicePhone.refreshToken()).andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + alicePhone.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anotherMembersTokenIsNotRevokedButTheAnswerIsTheSame() throws Exception {
        logout(alicePhone.accessToken(), bob.refreshToken()).andExpect(status().isNoContent());

        assertThat(revokedCount(bob)).isZero();
    }

    @Test
    void unknownTokenAnswers204() throws Exception {
        logout(alicePhone.accessToken(), "not-a-real-token").andExpect(status().isNoContent());
    }

    @Test
    void loggingOutTwiceAnswers204Both() throws Exception {
        logout(alicePhone.accessToken(), alicePhone.refreshToken()).andExpect(status().isNoContent());
        logout(alicePhone.accessToken(), alicePhone.refreshToken()).andExpect(status().isNoContent());
    }

    @Test
    void needsAnAccessToken() throws Exception {
        logout(null, alicePhone.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        logout("garbage", alicePhone.refreshToken()).andExpect(status().isUnauthorized());

        assertThat(revokedCount(alicePhone)).isZero();
    }

    @Test
    void rejectsAMissingRefreshToken() throws Exception {
        logout(alicePhone.accessToken(), null).andExpect(status().isBadRequest());
    }

    @Test
    void logoutAllRevokesEverySessionOfTheCallerOnly() throws Exception {
        mvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + alicePhone.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(revokedCount(alicePhone)).isEqualTo(2); // phone and laptop
        assertThat(revokedCount(bob)).isZero();
        assertThat(rowsOf(alicePhone)).extracting(r -> ((Number) r.get("updated_by")).longValue())
                .containsOnly(alicePhone.memberId());
    }

    @Test
    void logoutAllNeedsAnAccessToken() throws Exception {
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());

        assertThat(revokedCount(aliceLaptop)).isZero();
    }
}
