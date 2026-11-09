package com.englishprep.api.identity.application;

import com.englishprep.api.identity.domain.RefreshToken;
import com.englishprep.api.identity.domain.RefreshTokenRepository;
import com.englishprep.api.shared.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates refresh tokens and finds them again. The raw token is 32 random bytes; the database
 * only gets its SHA-256 hash. Unlike the access token, a refresh token is not a JWT: it means
 * nothing by itself and is valid only while its hash is stored and not revoked.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Makes a new token for the member, stores its hash and returns the raw value (the only copy). */
    @Transactional
    public IssuedRefreshToken issue(long memberId) {
        String value = generateValue();
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        tokens.save(RefreshToken.issue(memberId, hash(value), expiresAt));
        return new IssuedRefreshToken(value, expiresAt);
    }

    /** Looks the raw value up by its hash. Empty when unknown, revoked or expired (or null/blank). */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findActive(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hash(value)).filter(token -> token.isActive(clock.instant()));
    }

    /** 32 bytes from a cryptographically strong generator, written as URL-safe text (43 characters). */
    private String generateValue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 as 64 lower-case hex characters, the size of the {@code token_hash} column. */
    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
