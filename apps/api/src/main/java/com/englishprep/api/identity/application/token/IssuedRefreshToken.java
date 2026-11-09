package com.englishprep.api.identity.application.token;

import java.time.Instant;

/**
 * A freshly issued refresh token. {@code value} is the only copy of the raw token: it goes to the
 * client once and cannot be recovered later, because only its hash is stored.
 */
public record IssuedRefreshToken(String value, Instant expiresAt) {

    /** Keeps the token out of logs. */
    @Override
    public String toString() {
        return "IssuedRefreshToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
