package com.englishprep.api.identity.application.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.application.login.InvalidCredentialsException;
import com.englishprep.api.identity.application.login.IssueSessionUseCase;
import com.englishprep.api.identity.application.login.LoginResult;
import com.englishprep.api.identity.application.me.CurrentMemberNotFoundException;
import com.englishprep.api.identity.application.token.AccessToken;
import com.englishprep.api.identity.application.token.AccessTokenService;
import com.englishprep.api.identity.application.token.RefreshTokenService;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.domain.token.RefreshTokenRepository;
import com.englishprep.api.shared.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test: in-memory repositories and a fake password hasher ("h:" + password). */
class ChangePasswordUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private Member stored = new Member(7L, "Alice", "Nguyen", "alice01", "alice@example.com", "h:oldpass123",
            "UTC", NOW, NOW);
    private final List<RefreshToken> tokens = new ArrayList<>();

    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "h:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return passwordHash.equals("h:" + rawPassword);
        }
    };

    private final MemberRepository members = new MemberRepository() {
        @Override
        public Member save(Member member) {
            stored = member;
            return member;
        }

        @Override
        public Optional<Member> findById(Long id) {
            return stored != null && id == 7L ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public Optional<Member> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<Member> findByUserName(String userName) {
            return Optional.empty();
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public boolean existsByUserName(String userName) {
            return false;
        }
    };

    private final RefreshTokenService refreshTokens = new RefreshTokenService(new RefreshTokenRepository() {
        @Override
        public RefreshToken save(RefreshToken token) {
            tokens.add(token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return tokens.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean revokeIfActive(String tokenHash, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int revokeAllOfMember(long memberId, Instant now) {
            int revoked = 0;
            for (int i = 0; i < tokens.size(); i++) {
                RefreshToken t = tokens.get(i);
                if (t.memberId() == memberId && !t.isRevoked()) {
                    tokens.set(i, new RefreshToken(t.id(), t.memberId(), t.tokenHash(), t.expiresAt(), now));
                    revoked++;
                }
            }
            return revoked;
        }
    }, new JwtProperties("a".repeat(32), "english-prep", Duration.ofMinutes(15), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final AccessTokenService accessTokens = new AccessTokenService() {
        @Override
        public AccessToken issue(long memberId) {
            return new AccessToken("access-for-" + memberId, NOW.plusSeconds(900));
        }

        @Override
        public Optional<Long> verify(String token) {
            return Optional.empty();
        }
    };

    private final ChangePasswordUseCase useCase = new ChangePasswordUseCase(members, hasher, refreshTokens,
            new IssueSessionUseCase(accessTokens, refreshTokens));

    @Test
    void savesTheHashOfTheNewPassword() {
        useCase.change(7L, "oldpass123", "newpass456");

        assertThat(stored.passwordHash()).isEqualTo("h:newpass456");
        assertThat(stored.userName()).isEqualTo("alice01");
    }

    @Test
    void revokesEveryOldTokenButTheNewSessionWorks() {
        String phone = refreshTokens.issue(7L).value();
        String laptop = refreshTokens.issue(7L).value();

        LoginResult result = useCase.change(7L, "oldpass123", "newpass456");

        assertThat(refreshTokens.find(phone).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.find(laptop).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findActive(result.refreshToken().value())).isPresent();
        assertThat(result.accessToken().value()).isEqualTo("access-for-7");
    }

    @Test
    void doesNotTouchTheTokensOfOtherMembers() {
        String bobs = refreshTokens.issue(8L).value();

        useCase.change(7L, "oldpass123", "newpass456");

        assertThat(refreshTokens.find(bobs).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void wrongCurrentPasswordChangesNothing() {
        String token = refreshTokens.issue(7L).value();

        assertThatThrownBy(() -> useCase.change(7L, "wrong-password", "newpass456"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.change(7L, null, "newpass456"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(stored.passwordHash()).isEqualTo("h:oldpass123");
        assertThat(refreshTokens.findActive(token)).isPresent();
    }

    @Test
    void sameNewPasswordIsRejectedAndChangesNothing() {
        String token = refreshTokens.issue(7L).value();

        assertThatThrownBy(() -> useCase.change(7L, "oldpass123", "oldpass123"))
                .isInstanceOf(PasswordNotChangedException.class);

        assertThat(refreshTokens.findActive(token)).isPresent();
    }

    @Test
    void failsWhenTheMemberNoLongerExists() {
        stored = null;

        assertThatThrownBy(() -> useCase.change(7L, "oldpass123", "newpass456"))
                .isInstanceOf(CurrentMemberNotFoundException.class);
    }
}
