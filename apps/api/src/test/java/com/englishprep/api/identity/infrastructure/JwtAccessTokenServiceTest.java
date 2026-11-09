package com.englishprep.api.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.identity.application.AccessToken;
import com.englishprep.api.shared.security.JwtConfig;
import com.englishprep.api.shared.security.JwtProperties;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Plain unit test: no Spring context, the beans are built straight from {@link JwtConfig}. */
class JwtAccessTokenServiceTest {

    private static final String SECRET = "a".repeat(32);
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void issuedTokenVerifiesToTheSameMemberId() {
        JwtAccessTokenService service = serviceAt(NOW, SECRET, "english-prep");

        AccessToken token = service.issue(42L);

        assertThat(service.verify(token.value())).contains(42L);
    }

    @Test
    void expiresAtIsIssueTimePlusTheConfiguredLifetime() {
        AccessToken token = serviceAt(NOW, SECRET, "english-prep").issue(1L);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void tokenCarriesIssuerSubjectAndUniqueId() throws Exception {
        JwtAccessTokenService service = serviceAt(NOW, SECRET, "english-prep");

        String first = service.issue(7L).value();
        String second = service.issue(7L).value();

        JWTClaimsSet claims = com.nimbusds.jwt.SignedJWT.parse(first).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("english-prep");
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.getJWTID()).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = serviceAt(NOW, SECRET, "english-prep").issue(1L).value();

        JwtAccessTokenService later = serviceAt(NOW.plus(Duration.ofMinutes(15)).plusSeconds(1), SECRET, "english-prep");

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void acceptsATokenUpToTheMomentItExpires() {
        String token = serviceAt(NOW, SECRET, "english-prep").issue(1L).value();

        JwtAccessTokenService almost = serviceAt(NOW.plus(Duration.ofMinutes(15)), SECRET, "english-prep");

        assertThat(almost.verify(token)).contains(1L);
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        String token = serviceAt(NOW, "b".repeat(32), "english-prep").issue(1L).value();

        assertThat(serviceAt(NOW, SECRET, "english-prep").verify(token)).isEmpty();
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        String token = serviceAt(NOW, SECRET, "someone-else").issue(1L).value();

        assertThat(serviceAt(NOW, SECRET, "english-prep").verify(token)).isEmpty();
    }

    @Test
    void rejectsATamperedPayload() {
        JwtAccessTokenService service = serviceAt(NOW, SECRET, "english-prep");
        String[] parts = service.issue(1L).value().split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"english-prep\",\"sub\":\"2\",\"exp\":" + NOW.plusSeconds(600).getEpochSecond() + "}")
                        .getBytes());

        assertThat(service.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void rejectsAnUnsignedTokenWithAlgNone() {
        String unsigned = new PlainJWT(new PlainHeader(), new JWTClaimsSet.Builder()
                .issuer("english-prep").subject("1").expirationTime(java.util.Date.from(NOW.plusSeconds(600)))
                .build()).serialize();

        assertThat(serviceAt(NOW, SECRET, "english-prep").verify(unsigned)).isEmpty();
    }

    @Test
    void rejectsGarbageNullAndBlank() {
        JwtAccessTokenService service = serviceAt(NOW, SECRET, "english-prep");

        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify("")).isEmpty();
        assertThat(service.verify("   ")).isEmpty();
        assertThat(service.verify(null)).isEmpty();
    }

    @Test
    void toStringOfTheTokenDoesNotShowTheValue() {
        AccessToken token = serviceAt(NOW, SECRET, "english-prep").issue(1L);

        assertThat(token.toString()).doesNotContain(token.value());
    }

    private static JwtAccessTokenService serviceAt(Instant now, String secret, String issuer) {
        JwtProperties properties = new JwtProperties(secret, issuer, Duration.ofMinutes(15), Duration.ofDays(7));
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtConfig config = new JwtConfig();
        return new JwtAccessTokenService(
                config.jwtEncoder(properties), config.jwtDecoder(properties, clock), properties, clock);
    }
}
