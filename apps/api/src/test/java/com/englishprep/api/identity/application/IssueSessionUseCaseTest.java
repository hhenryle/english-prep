package com.englishprep.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.identity.domain.Member;
import com.englishprep.api.identity.domain.RefreshToken;
import com.englishprep.api.identity.domain.RefreshTokenRepository;
import com.englishprep.api.shared.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test: a fake access token service and the real refresh token service on an in-memory repository. */
class IssueSessionUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final List<RefreshToken> stored = new ArrayList<>();
    private final RefreshTokenService refreshTokens = new RefreshTokenService(new RefreshTokenRepository() {
        @Override
        public RefreshToken save(RefreshToken token) {
            stored.add(token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return stored.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
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
    private final IssueSessionUseCase useCase = new IssueSessionUseCase(accessTokens, refreshTokens);

    private static Member member(long id) {
        return new Member(id, "Alice", "Nguyen", "alice", "alice@example.com", "hash", "UTC", NOW, NOW);
    }

    @Test
    void returnsTheMemberWithBothTokensForThatMember() {
        LoginResult result = useCase.issue(member(7L));

        assertThat(result.member().id()).isEqualTo(7L);
        assertThat(result.accessToken().value()).isEqualTo("access-for-7");
        assertThat(result.refreshToken().value()).isNotBlank();
    }

    @Test
    void theRefreshTokenIsStoredForTheMemberAndCanBeFoundAgain() {
        LoginResult result = useCase.issue(member(7L));

        assertThat(stored).hasSize(1);
        assertThat(refreshTokens.findActive(result.refreshToken().value()))
                .get().extracting(RefreshToken::memberId).isEqualTo(7L);
    }

    @Test
    void everyCallStartsANewSession() {
        LoginResult first = useCase.issue(member(7L));
        LoginResult second = useCase.issue(member(7L));

        assertThat(first.refreshToken().value()).isNotEqualTo(second.refreshToken().value());
        assertThat(stored).hasSize(2);
    }
}
