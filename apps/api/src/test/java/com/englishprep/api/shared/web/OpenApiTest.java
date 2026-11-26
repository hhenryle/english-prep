package com.englishprep.api.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Checks the OpenAPI document that Swagger UI is built from. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiTest {

    @Autowired
    private MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions docs() throws Exception {
        return mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void declaresABearerJwtScheme() throws Exception {
        docs().andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void requiresTheSchemeByDefault() throws Exception {
        docs().andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void publicEndpointsOptOut() throws Exception {
        docs().andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post.security").isEmpty());
    }

    @Test
    void protectedEndpointsDoNotOptOut() throws Exception {
        docs().andExpect(jsonPath("$.paths['/api/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/me/password'].put.security").doesNotExist());
    }
}
