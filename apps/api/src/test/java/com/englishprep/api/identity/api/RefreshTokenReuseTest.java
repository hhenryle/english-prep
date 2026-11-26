package com.englishprep.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Reuse detection needs real commits: the mass revocation must survive the 401 that follows it. A test
 * wrapped in a rolled-back transaction would hide a missing {@code noRollbackFor}, so this one is NOT
 * transactional; it commits its own rows and deletes them afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenReuseTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private String firstRefresh;

    @BeforeEach
    void registerAndLogIn() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "alice01",
                 "email": "alice@example.com", "password": "secret123", "timeZone": "UTC"}
                """)).andExpect(status().isCreated());
        firstRefresh = JsonPath.read(login(), "$.refreshToken");
    }

    @AfterEach
    void removeCommittedRows() {
        jdbc.update("delete from member"); // refresh_token rows go with it (ON DELETE CASCADE)
        jdbc.update("delete from person");
    }

    private String login() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"alice01\", \"password\": \"secret123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int refreshStatus(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + token + "\"}")).andReturn().getResponse().getStatus();
    }

    private String refreshBody(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void reusingAnOldTokenAlsoRevokesTheTokenIssuedInItsPlace() throws Exception {
        String secondRefresh = JsonPath.read(refreshBody(firstRefresh), "$.refreshToken"); // normal rotation
        String otherDevice = JsonPath.read(login(), "$.refreshToken");

        assertThat(refreshStatus(firstRefresh)).isEqualTo(401); // the old token comes back

        // Both live tokens are now dead. If noRollbackFor were missing, these would still work.
        assertThat(refreshStatus(secondRefresh)).isEqualTo(401);
        assertThat(refreshStatus(otherDevice)).isEqualTo(401);
    }

    @Test
    void ofSeveralSimultaneousRefreshesWithOneTokenExactlyOneWins() throws Exception {
        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CyclicBarrier start = new CyclicBarrier(requests);
        List<Future<String[]>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                start.await(); // release all requests at once
                var response = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + firstRefresh + "\"}")).andReturn().getResponse();
                return new String[] {String.valueOf(response.getStatus()), response.getContentAsString()};
            }));
        }
        int winners = 0;
        String winnerRefresh = null;
        for (Future<String[]> result : results) {
            String[] r = result.get();
            if (r[0].equals("200")) {
                winners++;
                winnerRefresh = JsonPath.read(r[1], "$.refreshToken");
            } else {
                assertThat(r[0]).isEqualTo("401");
            }
        }
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
        // Losing a race is not theft: the winner's new token must still work.
        assertThat(refreshStatus(winnerRefresh)).isEqualTo(200);
    }
}
