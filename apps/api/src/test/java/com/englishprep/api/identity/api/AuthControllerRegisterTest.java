package com.englishprep.api.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import java.util.Map;
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
 * Calls POST /api/auth/register through the whole stack (controller, use case, JPA, a real
 * PostgreSQL in a container). Every test is rolled back, so tests do not see each other's rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerRegisterTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static String body(String userName, String email, String password, String timeZone) {
        String tz = timeZone == null ? "" : ", \"timeZone\": \"" + timeZone + "\"";
        return """
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "%s",
                 "email": "%s", "password": "%s"%s}
                """.formatted(userName, email, password, tz);
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void createsTheAccountAndReturns201WithoutThePassword() throws Exception {
        register(body("Alice01", "Alice@Example.com", "secret123", "Asia/Ho_Chi_Minh"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userName").value("alice01"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void storesABCryptHashNotThePassword() throws Exception {
        register(body("alice", "alice@example.com", "secret123", null)).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap(
                "select p.password_hash, p.person_type, p.created_by, p.exclusion_cd, m.time_zone "
                        + "from person p join member m on m.member_id = p.person_id where p.user_nm = 'alice'");

        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("secret123");
        assertThat(row.get("person_type")).isEqualTo("MEMBER");
        assertThat(((Number) row.get("created_by")).longValue()).isZero();
        assertThat(row.get("exclusion_cd")).isEqualTo("IN");
        assertThat(row.get("time_zone")).isEqualTo("UTC");
    }

    @Test
    void returns409WhenTheEmailIsAlreadyUsedEvenWithDifferentCase() throws Exception {
        register(body("alice", "alice@example.com", "secret123", null)).andExpect(status().isCreated());

        register(body("bob", "ALICE@example.com", "secret123", null))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Email is already registered"));
    }

    @Test
    void returns409WhenTheUserNameIsAlreadyTaken() throws Exception {
        register(body("alice", "alice@example.com", "secret123", null)).andExpect(status().isCreated());

        register(body("ALICE", "other@example.com", "secret123", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("User name is already taken"));
    }

    @Test
    void returns400ListingEveryInvalidField() throws Exception {
        register("""
                {"firstName": "", "lastName": "N", "userName": "a b", "email": "not-an-email",
                 "password": "short", "timeZone": "Mars/Olympus"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.userName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.timeZone").exists())
                .andExpect(jsonPath("$.errors.lastName").doesNotExist());
    }

    @Test
    void returns400WhenFieldsAreMissing() throws Exception {
        register("{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void returns400WhenThePasswordIsLongerThan72Bytes() throws Exception {
        // 40 characters, 3 bytes each in UTF-8 = 120 bytes: short in characters, too long for BCrypt.
        String password = "€".repeat(40);

        register(body("alice", "alice@example.com", password, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void returns400ForMalformedJson() throws Exception {
        register("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void theEndpointAppearsInTheOpenApiDocument() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists());
    }
}
