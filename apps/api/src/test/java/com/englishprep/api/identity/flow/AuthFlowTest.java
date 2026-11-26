package com.englishprep.api.identity.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.englishprep.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole authentication story as a person lives it, through the real endpoints and a real PostgreSQL.
 * Each test is a scenario made of several calls. The class is NOT transactional: everything is really
 * committed (as in production), so rollback cannot hide a mistake. Rows are deleted after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeCommittedRows() {
        jdbc.update("delete from member"); // refresh_token rows go with it (ON DELETE CASCADE)
        jdbc.update("delete from person");
    }

    /** What the client keeps after a login or a refresh. */
    private record Session(String access, String refresh) {
    }

    private int send(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String access) {
        return request.header("Authorization", "Bearer " + access);
    }

    private void register(String userName) throws Exception {
        assertThat(send(json(post("/api/auth/register"), """
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "%s",
                 "email": "%s@example.com", "password": "oldpass123", "timeZone": "UTC"}
                """.formatted(userName, userName)))).isEqualTo(201);
    }

    private int loginStatus(String userName, String password) throws Exception {
        return send(json(post("/api/auth/login"), "{\"login\": \"" + userName + "\", \"password\": \"" + password + "\"}"));
    }

    private Session login(String userName, String password) throws Exception {
        String body = mvc.perform(json(post("/api/auth/login"),
                        "{\"login\": \"" + userName + "\", \"password\": \"" + password + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private Session sessionFrom(String body) {
        return new Session(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    private int refreshStatus(Session session) throws Exception {
        return send(json(post("/api/auth/refresh"), "{\"refreshToken\": \"" + session.refresh() + "\"}"));
    }

    private int meStatus(Session session) throws Exception {
        return send(withToken(get("/api/me"), session.access()));
    }

    @Test
    void aPersonUsingTwoDevicesFromSignUpToLogout() throws Exception {
        // 1. Sign up, log in on the phone and on the laptop. Both can see their account.
        register("alice01");
        Session phone = login("alice01", "oldpass123");
        Session laptop = login("alice01", "oldpass123");
        assertThat(meStatus(phone)).isEqualTo(200);
        assertThat(meStatus(laptop)).isEqualTo(200);

        // 2. Change the time zone on the phone; the laptop sees it too (it is the same account).
        assertThat(send(json(withToken(put("/api/me/time-zone"), phone.access()), "{\"timeZone\": \"Asia/Ho_Chi_Minh\"}")))
                .isEqualTo(200);
        String me = mvc.perform(withToken(get("/api/me"), laptop.access())).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(me, "$.timeZone")).isEqualTo("Asia/Ho_Chi_Minh");

        // 3. The phone refreshes: it gets a new pair, and the old refresh token is spent.
        String refreshed = mvc.perform(json(post("/api/auth/refresh"), "{\"refreshToken\": \"" + phone.refresh() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        Session phoneAfterRefresh = sessionFrom(refreshed);
        assertThat(meStatus(phoneAfterRefresh)).isEqualTo(200);

        // 4. The phone changes the password and gets a fresh session. The laptop is logged out for good:
        //    its refresh token is dead (its access token simply runs out within 15 minutes).
        String changed = mvc.perform(json(withToken(put("/api/me/password"), phoneAfterRefresh.access()),
                        "{\"currentPassword\": \"oldpass123\", \"newPassword\": \"newpass456\"}"))
                .andReturn().getResponse().getContentAsString();
        Session phoneAfterChange = sessionFrom(changed);
        assertThat(meStatus(phoneAfterChange)).isEqualTo(200);
        assertThat(refreshStatus(phoneAfterChange)).isEqualTo(200); // the phone keeps working
        assertThat(refreshStatus(laptop)).isEqualTo(401);           // the laptop must log in again

        // 5. Only the new password works from now on.
        assertThat(loginStatus("alice01", "oldpass123")).isEqualTo(401);
        Session laptopAgain = login("alice01", "newpass456");
        assertThat(meStatus(laptopAgain)).isEqualTo(200);

        // 6. The laptop logs out: its refresh token no longer works.
        assertThat(send(json(withToken(post("/api/auth/logout"), laptopAgain.access()),
                "{\"refreshToken\": \"" + laptopAgain.refresh() + "\"}"))).isEqualTo(204);
        assertThat(refreshStatus(laptopAgain)).isEqualTo(401);
    }

    @Test
    void logoutAllEndsEveryDeviceAndTheAccountStillWorks() throws Exception {
        register("alice01");
        Session phone = login("alice01", "oldpass123");
        Session laptop = login("alice01", "oldpass123");

        assertThat(send(withToken(post("/api/auth/logout-all"), phone.access()))).isEqualTo(204);

        assertThat(refreshStatus(phone)).isEqualTo(401);
        assertThat(refreshStatus(laptop)).isEqualTo(401);
        assertThat(loginStatus("alice01", "oldpass123")).isEqualTo(200); // logging in again is always possible
    }

    @Test
    void aStolenTokenKillsEverySessionOfItsOwnerAndNobodyElses() throws Exception {
        register("alice01");
        register("bob01");
        Session alice = login("alice01", "oldpass123");
        Session bob = login("bob01", "oldpass123");

        // A thief and Alice both hold alice's refresh token. The first one to use it wins...
        String rotated = mvc.perform(json(post("/api/auth/refresh"), "{\"refreshToken\": \"" + alice.refresh() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        Session aliceNewest = sessionFrom(rotated);

        // ...and when the other one shows up with the old token, all of Alice's sessions are closed.
        assertThat(refreshStatus(alice)).isEqualTo(401);
        assertThat(refreshStatus(aliceNewest)).isEqualTo(401);

        // Bob is not affected.
        assertThat(refreshStatus(bob)).isEqualTo(200);
        assertThat(loginStatus("alice01", "oldpass123")).isEqualTo(200);
    }

    @Test
    void aLoggedOutPersonCannotUseAnyProtectedCallWithTheOldRefreshToken() throws Exception {
        register("alice01");
        Session phone = login("alice01", "oldpass123");
        send(json(withToken(post("/api/auth/logout"), phone.access()), "{\"refreshToken\": \"" + phone.refresh() + "\"}"));

        // Without a way to refresh, the client has to start again from login.
        assertThat(refreshStatus(phone)).isEqualTo(401);
        assertThat(loginStatus("alice01", "oldpass123")).isEqualTo(200);
    }
}
