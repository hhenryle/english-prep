package com.englishprep.api.identity.domain.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant EXPIRES = Instant.parse("2026-01-08T10:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void aNewTokenIsActiveUntilItExpires() {
        RefreshToken token = RefreshToken.issue(1L, HASH, EXPIRES);

        assertThat(token.id()).isNull();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(token.isActive(EXPIRES.minusSeconds(1))).isTrue();
    }

    @Test
    void itIsStillValidAtTheExactMomentOfExpiryAndExpiredOneSecondLater() {
        RefreshToken token = RefreshToken.issue(1L, HASH, EXPIRES);

        assertThat(token.isActive(EXPIRES)).isTrue();
        assertThat(token.isExpired(EXPIRES.plusSeconds(1))).isTrue();
        assertThat(token.isActive(EXPIRES.plusSeconds(1))).isFalse();
    }

    @Test
    void aRevokedTokenIsNeverActiveEvenBeforeItExpires() {
        RefreshToken token = new RefreshToken(5L, 1L, HASH, EXPIRES, EXPIRES.minusSeconds(60));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.isExpired(EXPIRES.minusSeconds(120))).isFalse();
        assertThat(token.isActive(EXPIRES.minusSeconds(120))).isFalse();
    }

    @Test
    void aTokenNeedsAHashAndAnExpiryTime() {
        assertThatThrownBy(() -> RefreshToken.issue(1L, " ", EXPIRES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, HASH, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
