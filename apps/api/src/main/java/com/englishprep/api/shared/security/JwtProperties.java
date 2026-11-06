package com.englishprep.api.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT settings, read from {@code app.jwt.*} in application.yml.
 *
 * @param secret          key that signs and verifies tokens (HS256), from the JWT_SECRET environment variable
 * @param issuer          written in every token as "iss" and checked when a token is read
 * @param accessTokenTtl  how long an access token is valid
 * @param refreshTokenTtl how long a refresh token is valid
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("english-prep") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    /** Fails at startup, with a clear message, instead of failing later when the first token is made. */
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret is not set: define the JWT_SECRET environment variable");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (HS256 needs a 256-bit key)");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.jwt.issuer must not be blank");
        }
        requirePositive(accessTokenTtl, "app.jwt.access-token-ttl");
        requirePositive(refreshTokenTtl, "app.jwt.refresh-token-ttl");
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }

    /** Keeps the signing key out of logs. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + "]";
    }
}
