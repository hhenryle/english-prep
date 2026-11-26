package com.englishprep.api.identity.application.password;

import com.englishprep.api.identity.application.login.InvalidCredentialsException;
import com.englishprep.api.identity.application.login.IssueSessionUseCase;
import com.englishprep.api.identity.application.login.LoginResult;
import com.englishprep.api.identity.application.me.CurrentMemberNotFoundException;
import com.englishprep.api.identity.application.token.RefreshTokenService;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the password of the logged-in member. Every refresh token is revoked, so other devices
 * must log in again, and a new session is issued for the device that made the request.
 */
@Service
public class ChangePasswordUseCase {

    private final MemberRepository members;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenService refreshTokens;
    private final IssueSessionUseCase issueSession;

    public ChangePasswordUseCase(MemberRepository members, PasswordHasher passwordHasher,
            RefreshTokenService refreshTokens, IssueSessionUseCase issueSession) {
        this.members = members;
        this.passwordHasher = passwordHasher;
        this.refreshTokens = refreshTokens;
        this.issueSession = issueSession;
    }

    /**
     * The member is read from the database (not trusted from the access token alone), the current
     * password must match, and the new one must differ. The order matters: tokens are revoked first, then
     * the new session is issued, so the new refresh token is not revoked along with the old ones.
     */
    @Transactional
    public LoginResult change(long memberId, String currentPassword, String newPassword) {
        Member member = members.findById(memberId).orElseThrow(CurrentMemberNotFoundException::new);

        if (!passwordHasher.matches(currentPassword == null ? "" : currentPassword, member.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (passwordHasher.matches(newPassword, member.passwordHash())) {
            throw new PasswordNotChangedException();
        }

        Member updated = members.save(member.withPasswordHash(passwordHasher.hash(newPassword)));
        refreshTokens.revokeAllOf(memberId);
        return issueSession.issue(updated);
    }
}
