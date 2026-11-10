package com.englishprep.api.identity.application.token;

import java.util.Optional;

/** Creates and checks short-lived access tokens. The application layer does not know the token format. */
public interface AccessTokenService {

    /** Issues a token for the given member. */
    AccessToken issue(long memberId);

    /**
     * Checks signature, issuer and expiry.
     *
     * @return the member id inside the token, or empty when the token is invalid, expired or not ours
     */
    Optional<Long> verify(String token);
}
