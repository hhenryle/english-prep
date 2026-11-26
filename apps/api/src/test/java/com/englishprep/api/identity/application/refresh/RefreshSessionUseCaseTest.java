package com.englishprep.api.identity.application.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.application.login.IssueSessionUseCase;
import com.englishprep.api.identity.application.login.LoginResult;
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

/** Plain unit test: in-memory repositories, a fake access token service, and a clock we can move. */
class RefreshSessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Member ALICE = new Member(7L, "Alice", "Nguyen", "alice01", "alice@example.com", "hash",
            "UTC", NOW, NOW);

    private final InMemoryTokens tokens = new InMemoryTokens();
    private boolean aliceExists = true;

    private final MemberRepository members = new MemberRepository() {
        @Override
        public Member save(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Member> findById(Long id) {
            return aliceExists && id == 7L ? Optional.of(ALICE) : Optional.empty();
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

    /** The use case, with the clock at the given moment. */
    private RefreshSessionUseCase useCaseAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        JwtProperties properties = new JwtProperties("a".repeat(32), "english-prep",
                Duration.ofMinutes(15), Duration.ofDays(7));
        RefreshTokenService refreshTokens = new RefreshTokenService(tokens, properties, clock);
        return new RefreshSessionUseCase(refreshTokens, members,
                new IssueSessionUseCase(accessTokens, refreshTokens), clock);
    }

    private String issueTokenFor(long memberId) {
        JwtProperties properties = new JwtProperties("a".repeat(32), "english-prep",
                Duration.ofMinutes(15), Duration.ofDays(7));
        return new RefreshTokenService(tokens, properties, Clock.fixed(NOW, ZoneOffset.UTC))
                .issue(memberId).value();
    }

    @Test
    void returnsANewSessionForTheTokenOwner() {
        String old = issueTokenFor(7L);

        LoginResult result = useCaseAt(NOW).refresh(old);

        assertThat(result.member().id()).isEqualTo(7L);
        assertThat(result.accessToken().value()).isEqualTo("access-for-7");
        assertThat(result.refreshToken().value()).isNotBlank().isNotEqualTo(old);
    }

    @Test
    void revokesTheOldTokenAndTheNewOneWorks() {
        String old = issueTokenFor(7L);

        String fresh = useCaseAt(NOW).refresh(old).refreshToken().value();

        assertThat(tokens.stateOf(old).isRevoked()).isTrue();
        assertThat(tokens.stateOf(fresh).isRevoked()).isFalse();
        // the new token can be exchanged again
        assertThat(useCaseAt(NOW).refresh(fresh).refreshToken().value()).isNotEqualTo(fresh);
    }

    @Test
    void rejectsAnUnknownOrBlankToken() {
        assertThatThrownBy(() -> useCaseAt(NOW).refresh("unknown")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> useCaseAt(NOW).refresh(" ")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> useCaseAt(NOW).refresh(null)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsAnExpiredTokenAndLeavesTheMembersOtherTokensAlone() {
        String expired = issueTokenFor(7L);
        String other = issueTokenFor(7L);
        Instant later = NOW.plus(Duration.ofDays(7)).plusSeconds(1);

        assertThatThrownBy(() -> useCaseAt(later).refresh(expired)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(tokens.stateOf(other).isRevoked()).isFalse();
        assertThat(tokens.stateOf(expired).isRevoked()).isFalse();
    }

    @Test
    void reusingARevokedTokenRevokesEverySessionOfTheMember() {
        String stolen = issueTokenFor(7L);
        String current = useCaseAt(NOW).refresh(stolen).refreshToken().value(); // normal rotation
        String otherDevice = issueTokenFor(7L);
        String otherMember = issueTokenFor(8L);

        assertThatThrownBy(() -> useCaseAt(NOW).refresh(stolen)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(tokens.stateOf(current).isRevoked()).isTrue();
        assertThat(tokens.stateOf(otherDevice).isRevoked()).isTrue();
        assertThat(tokens.stateOf(otherMember).isRevoked()).isFalse();
    }

    @Test
    void rejectsATokenWhoseMemberWasDeletedAndKeepsTheToken() {
        String token = issueTokenFor(7L);
        aliceExists = false;

        assertThatThrownBy(() -> useCaseAt(NOW).refresh(token)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(tokens.stateOf(token).isRevoked()).isFalse();
    }

    @Test
    void losingTheRaceIsRejectedWithoutRevokingTheOtherTokens() {
        String token = issueTokenFor(7L);
        String other = issueTokenFor(7L);
        tokens.loseNextRevoke = true; // another request revokes the token between our check and our revoke

        assertThatThrownBy(() -> useCaseAt(NOW).refresh(token)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(tokens.stateOf(other).isRevoked()).isFalse();
    }

    /** In-memory repository; {@code loseNextRevoke} makes the next revokeIfActive answer "someone else won". */
    private static class InMemoryTokens implements RefreshTokenRepository {

        final List<RefreshToken> saved = new ArrayList<>();
        boolean loseNextRevoke;

        RefreshToken stateOf(String value) {
            return findByTokenHash(hashOf(value)).orElseThrow();
        }

        private static String hashOf(String value) {
            try {
                var method = RefreshTokenService.class.getDeclaredMethod("hash", String.class);
                method.setAccessible(true);
                return (String) method.invoke(null, value);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public RefreshToken save(RefreshToken token) {
            RefreshToken stored = new RefreshToken((long) saved.size() + 1, token.memberId(), token.tokenHash(),
                    token.expiresAt(), token.revokedAt());
            saved.add(stored);
            return stored;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return saved.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean revokeIfActive(String tokenHash, Instant now) {
            if (loseNextRevoke) {
                loseNextRevoke = false;
                return false;
            }
            for (int i = 0; i < saved.size(); i++) {
                RefreshToken token = saved.get(i);
                if (token.tokenHash().equals(tokenHash) && token.isActive(now)) {
                    saved.set(i, new RefreshToken(token.id(), token.memberId(), token.tokenHash(),
                            token.expiresAt(), now));
                    return true;
                }
            }
            return false;
        }

        @Override
        public int revokeAllOfMember(long memberId, Instant now) {
            int revoked = 0;
            for (int i = 0; i < saved.size(); i++) {
                RefreshToken token = saved.get(i);
                if (token.memberId() == memberId && !token.isRevoked()) {
                    saved.set(i, new RefreshToken(token.id(), token.memberId(), token.tokenHash(),
                            token.expiresAt(), now));
                    revoked++;
                }
            }
            return revoked;
        }
    }
}
