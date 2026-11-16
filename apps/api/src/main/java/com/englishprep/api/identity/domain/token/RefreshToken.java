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

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Expired once {@code now} is after the expiry time; at exactly that moment it is still valid. */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Usable when it is not revoked and not expired. */
    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
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
