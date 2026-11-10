package com.englishprep.api.identity.api.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.application.token.AccessTokenService;
import com.englishprep.api.shared.security.JwtProperties;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole flow through the real stack: register, log in, call a protected endpoint with the access
 * token that the login returned, and call it with tokens that must be refused. GET /api/me is the
 * protected endpoint used here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BearerAuthenticationTest {

    private static final String PROTECTED = "/api/me";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService accessTokens;

    @Autowired
    private JwtProperties properties;

    @Autowired
    private JwtEncoder encoder;

    private String loginAsAlice() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Alice", "lastName": "Nguyen", "userName": "alice01",
                 "email": "alice@example.com", "password": "secret123"}
                """)).andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\": \"alice01\", \"password\": \"secret123\"}"))
                .andReturn().getResponse().getContentAsString();
    }

    private static String claims(Instant issuedAt, Instant expiresAt, JwtEncoder using) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("english-prep").subject("1")
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
        return using.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void theAccessTokenFromLoginOpensAProtectedEndpointAsThatMember() throws Exception {
        String body = loginAsAlice();
        long memberId = ((Number) JsonPath.read(body, "$.member.id")).longValue();
        String accessToken = JsonPath.read(body, "$.accessToken");

        mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId));
    }

    @Test
    void aTokenIssuedDirectlyForARegisteredMemberAlsoWorks() throws Exception {
        long memberId = ((Number) JsonPath.read(loginAsAlice(), "$.member.id")).longValue();
        String token = accessTokens.issue(memberId).value();

        mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(memberId));
    }

    @Test
    void noTokenIs401WithAProblemDetail() throws Exception {
        mvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void garbageAndWrongSchemeAreRefused() throws Exception {
        mvc.perform(get(PROTECTED).header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get(PROTECTED).header("Authorization", "Basic dXNlcjpwYXNz")).andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsRefused() throws Exception {
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        String expired = claims(longAgo, longAgo.plus(Duration.ofMinutes(15)), encoder);

        mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRefused() throws Exception {
        JwtEncoder other = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("z".repeat(40).getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = claims(Instant.now(), Instant.now().plus(properties.accessTokenTtl()), other);

        mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void aBadTokenDoesNotBreakAPublicEndpoint() throws Exception {
        // The empty body is rejected by validation (400), not by security (401).
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer old-expired-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
