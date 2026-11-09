package com.englishprep.api.identity.domain.token;

import java.time.Instant;

/**
 * A refresh token as the application sees it. The token value itself is never kept: only its
 * SHA-256 hash, so a leaked database cannot be used to refresh sessions.
 */
public class RefreshToken {

    private final Long id;
    private final long memberId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant revokedAt;

    public RefreshToken(Long id, long memberId, String tokenHash, Instant expiresAt, Instant revokedAt) {
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("tokenHash must not be blank");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        this.id = id;
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    /** A new token for a member. The id is null until the token is saved. */
    public static RefreshToken issue(long memberId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(null, memberId, tokenHash, expiresAt, null);
    }

    /** Usable when it is not revoked and {@code now} is not after the expiry time. */
    public boolean isActive(Instant now) {
        return revokedAt == null && !now.isAfter(expiresAt);
    }

    public Long id() {
        return id;
    }

    public long memberId() {
        return memberId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
