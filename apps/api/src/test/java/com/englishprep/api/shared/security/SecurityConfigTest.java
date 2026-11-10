package com.englishprep.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks the access rules of {@link SecurityConfig} through the whole stack, without sending any token:
 * public paths must work, everything else must answer 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    private static final String EMPTY_JSON = "{}";

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiDocumentationIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void registerAndLoginArePublic() throws Exception {
        // An empty body is rejected by validation (400): the request got past security to the controller.
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(EMPTY_JSON))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(EMPTY_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyOtherPathNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void the401HasAProblemDetailBodyAndAskForABearerToken() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(jsonPath("$.instance").value("/api/me"));
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void aPostWithoutATokenIs401NotACsrf403() throws Exception {
        mvc.perform(post("/api/anything").contentType(MediaType.APPLICATION_JSON).content(EMPTY_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyThePostMethodIsOpenOnTheAuthEndpoints() throws Exception {
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void noSessionCookieIsEverSet() throws Exception {
        MvcResult denied = mvc.perform(get("/api/me")).andReturn();
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\": \"nobody\", \"password\": \"secret123\"}")).andReturn();

        assertThat(denied.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(login.getResponse().getHeaders("Set-Cookie")).isEmpty();
        assertThat(login.getResponse().getStatus()).isEqualTo(401);
    }
}
