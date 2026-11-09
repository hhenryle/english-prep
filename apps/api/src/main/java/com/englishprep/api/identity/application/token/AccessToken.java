package com.englishprep.api.identity.application.token;

import java.time.Instant;

/**
 * A freshly issued access token.
 *
 * @param value     the compact JWT string the client sends back as {@code Authorization: Bearer ...}
 * @param expiresAt the moment after which the token is no longer accepted
 */
public record AccessToken(String value, Instant expiresAt) {

    /** Keeps the token out of logs. */
    @Override
    public String toString() {
        return "AccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
