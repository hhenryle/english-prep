package com.englishprep.api.identity.domain.token;

import java.util.Optional;

public interface RefreshTokenRepository {

    /** Inserts when the token has no id, otherwise updates it. Returns the saved token. */
    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
