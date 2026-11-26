package com.englishprep.api.identity.application.logout;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.identity.application.token.RefreshTokenService;
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

/** Plain unit test with an in-memory repository. */
class LogoutUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final List<RefreshToken> saved = new ArrayList<>();
    private final RefreshTokenService refreshTokens = new RefreshTokenService(new RefreshTokenRepository() {
        @Override
        public RefreshToken save(RefreshToken token) {
            saved.add(token);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHash(String tokenHash) {
            return saved.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean revokeIfActive(String tokenHash, Instant now) {
            for (int i = 0; i < saved.size(); i++) {
                RefreshToken t = saved.get(i);
                if (t.tokenHash().equals(tokenHash) && t.isActive(now)) {
                    saved.set(i, new RefreshToken(t.id(), t.memberId(), t.tokenHash(), t.expiresAt(), now));
                    return true;
                }
            }
            return false;
        }

        @Override
        public int revokeAllOfMember(long memberId, Instant now) {
            int revoked = 0;
            for (int i = 0; i < saved.size(); i++) {
                RefreshToken t = saved.get(i);
                if (t.memberId() == memberId && !t.isRevoked()) {
                    saved.set(i, new RefreshToken(t.id(), t.memberId(), t.tokenHash(), t.expiresAt(), now));
                    revoked++;
                }
            }
            return revoked;
        }
    }, new JwtProperties("a".repeat(32), "english-prep", Duration.ofMinutes(15), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final LogoutUseCase useCase = new LogoutUseCase(refreshTokens);

    private boolean revoked(String value) {
        return refreshTokens.find(value).orElseThrow().isRevoked();
    }

    @Test
    void revokesTheCallersToken() {
        String token = refreshTokens.issue(7L).value();

        useCase.logout(7L, token);

        assertThat(revoked(token)).isTrue();
    }

    @Test
    void doesNotRevokeATokenOfAnotherMember() {
        String bobs = refreshTokens.issue(8L).value();

        useCase.logout(7L, bobs);

        assertThat(revoked(bobs)).isFalse();
    }

    @Test
    void onlyEndsTheGivenSession() {
        String phone = refreshTokens.issue(7L).value();
        String laptop = refreshTokens.issue(7L).value();

        useCase.logout(7L, phone);

        assertThat(revoked(phone)).isTrue();
        assertThat(revoked(laptop)).isFalse();
    }

    @Test
    void ignoresUnknownBlankNullAndAlreadyRevokedTokens() {
        String token = refreshTokens.issue(7L).value();
        useCase.logout(7L, token);

        useCase.logout(7L, token); // twice
        useCase.logout(7L, "unknown");
        useCase.logout(7L, " ");
        useCase.logout(7L, null);

        assertThat(revoked(token)).isTrue();
    }

    @Test
    void logoutAllRevokesEveryTokenOfTheMemberOnly() {
        String phone = refreshTokens.issue(7L).value();
        String laptop = refreshTokens.issue(7L).value();
        String bobs = refreshTokens.issue(8L).value();

        int count = useCase.logoutAll(7L);

        assertThat(count).isEqualTo(2);
        assertThat(revoked(phone)).isTrue();
        assertThat(revoked(laptop)).isTrue();
        assertThat(revoked(bobs)).isFalse();
    }
}
