package com.englishprep.api.identity.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.englishprep.api.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Finds every endpoint the application really exposes under /api and calls it without a token: only the
 * endpoints on the public list may answer anything but 401. A new endpoint whose protection was forgotten
 * (or a public one nobody declared here) makes this test fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EndpointsAreProtectedTest {

    /** The only endpoints that may be called without a token. Keep in sync with SecurityConfig. */
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/refresh");

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    /** "METHOD /path" for every endpoint under /api, with path variables replaced by 1. */
    private List<String> apiEndpoints() {
        List<String> endpoints = new ArrayList<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.englishprep.api")) {
                continue; // skip framework endpoints (error page, springdoc, ...)
            }
            var patterns = info.getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (var method : info.getMethodsCondition().getMethods()) {
                    endpoints.add(method + " " + pattern.replaceAll("\\{[^}]+}", "1"));
                }
            }
        }
        return endpoints;
    }

    @Test
    void theApplicationHasEndpointsToCheck() {
        // Guards the test itself: if discovery found nothing, the next test would pass for the wrong reason.
        assertThat(apiEndpoints()).contains("POST /api/auth/login", "GET /api/me", "POST /api/auth/logout");
    }

    @Test
    void everyEndpointExceptThePublicOnesAnswers401WithoutAToken() throws Exception {
        List<String> failures = new ArrayList<>();
        for (String endpoint : apiEndpoints()) {
            String[] parts = endpoint.split(" ");
            int status = mvc.perform(request(HttpMethod.valueOf(parts[0]), parts[1])).andReturn().getResponse().getStatus();
            boolean isPublic = PUBLIC.contains(endpoint);
            if (isPublic == (status == 401)) {
                failures.add(endpoint + " answered " + status + (isPublic ? " but should be public" : " but should need a token"));
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void thePublicListHasNoStaleEntries() {
        assertThat(apiEndpoints()).containsAll(PUBLIC);
    }
}
