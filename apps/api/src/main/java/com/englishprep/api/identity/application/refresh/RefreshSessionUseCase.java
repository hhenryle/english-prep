package com.englishprep.api.identity.application.refresh;

import com.englishprep.api.identity.application.login.IssueSessionUseCase;
import com.englishprep.api.identity.application.login.LoginResult;
import com.englishprep.api.identity.application.token.RefreshTokenService;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import com.englishprep.api.identity.domain.token.RefreshToken;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchanges a refresh token for a new session (rotation): the old token is revoked and a new
 * access token and refresh token are issued. Using a token that was already revoked is treated as
 * theft: every open token of that member is revoked.
 */
@Service
public class RefreshSessionUseCase {

    private final RefreshTokenService refreshTokens;
    private final MemberRepository members;
    private final IssueSessionUseCase issueSession;
    private final Clock clock;

    public RefreshSessionUseCase(RefreshTokenService refreshTokens, MemberRepository members,
            IssueSessionUseCase issueSession, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.members = members;
        this.issueSession = issueSession;
        this.clock = clock;
    }

    /**
     * {@code noRollbackFor} is essential: on reuse we revoke all tokens and then throw. Without it Spring
     * would roll the transaction back because of the exception and the mass revocation would be lost.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResult refresh(String value) {
        RefreshToken token = refreshTokens.find(value).orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            // An old token came back: either the thief or the real owner is using it. Close every session.
            refreshTokens.revokeAllOf(token.memberId());
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(clock.instant())) {
            throw new InvalidRefreshTokenException();
        }
        Member member = members.findById(token.memberId()).orElseThrow(InvalidRefreshTokenException::new);

        // Only one of several simultaneous requests wins here. The others lost a race, not a theft: no mass revoke.
        if (!refreshTokens.revoke(value)) {
            throw new InvalidRefreshTokenException();
        }
        return issueSession.issue(member);
    }
}
