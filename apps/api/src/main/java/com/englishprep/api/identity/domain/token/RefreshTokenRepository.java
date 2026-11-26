package com.englishprep.api.identity.domain.token;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {

    /** Inserts when the token has no id, otherwise updates it. Returns the saved token. */
    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes the token if it is still active (not revoked, not expired). Returns true only for the call
     * that did it, so when several requests use the same token at the same time exactly one gets true.
     * Must run inside a transaction.
     */
    boolean revokeIfActive(String tokenHash, Instant now);

    /** Revokes every token of the member that is not revoked yet and returns how many were revoked. */
    int revokeAllOfMember(long memberId, Instant now);
}
