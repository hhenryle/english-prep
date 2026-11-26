package com.englishprep.api.identity.application.logout;

import com.englishprep.api.identity.application.token.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ends sessions by revoking refresh tokens. The caller is already authenticated by an access token. */
@Service
public class LogoutUseCase {

    private final RefreshTokenService refreshTokens;

    public LogoutUseCase(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /**
     * Ends one session. The token is revoked only when it belongs to the caller, so nobody can log
     * another member out with a token they found. Nothing is reported either way (unknown, expired,
     * already revoked, someone else's): the endpoint always answers the same, so it cannot be used to
     * test which tokens exist.
     */
    @Transactional
    public void logout(long memberId, String refreshTokenValue) {
        refreshTokens.find(refreshTokenValue)
                .filter(token -> token.memberId() == memberId)
                .ifPresent(token -> refreshTokens.revoke(refreshTokenValue));
    }

    /** Ends every session of the member (all devices). Returns how many tokens were revoked. */
    @Transactional
    public int logoutAll(long memberId) {
        return refreshTokens.revokeAllOf(memberId);
    }
}
