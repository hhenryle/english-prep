package com.englishprep.api.identity.application;

import com.englishprep.api.identity.domain.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an authenticated member into a session: one access token and one refresh token. Every
 * login method (password now, others later) ends here, so tokens are issued in one place.
 */
@Service
public class IssueSessionUseCase {

    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;

    public IssueSessionUseCase(AccessTokenService accessTokens, RefreshTokenService refreshTokens) {
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    /** The member must already be authenticated by the caller; this does not check anything. */
    @Transactional
    public LoginResult issue(Member member) {
        return new LoginResult(member, accessTokens.issue(member.id()), refreshTokens.issue(member.id()));
    }
}
