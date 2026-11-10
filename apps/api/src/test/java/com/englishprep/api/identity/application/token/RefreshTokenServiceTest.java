package com.englishprep.api.identity.application.token;

import static org.assertj.core.api.Assertions.assertThat;

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

/** Plain unit test: in-memory repository and a fixed clock. */
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final InMemoryRefreshTokenRepository repository = new InMemoryRefreshTokenRepository();

    private RefreshTokenService serviceAt(Instant now) {
        JwtProperties properties = new JwtProperties("a".repeat(32), "english-prep",
                Duration.ofMinutes(15), Duration.ofDays(7));
        return new RefreshTokenService(repository, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void storesOnlyTheHashOfTheToken() {
        IssuedRefreshToken issued = serviceAt(NOW).issue(5L);

        assertThat(repository.saved).hasSize(1);
        RefreshToken stored = repository.saved.get(0);
        assertThat(stored.memberId()).isEqualTo(5L);
        assertThat(stored.tokenHash()).isEqualTo(RefreshTokenService.hash(issued.value()))
                .isNotEqualTo(issued.value()).hasSize(64);
    }

    @Test
    void expiresAtIsIssueTimePlusTheConfiguredLifetime() {
        IssuedRefreshToken issued = serviceAt(NOW).issue(1L);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(repository.saved.get(0).expiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void everyTokenIsDifferentAndLongEnough() {
        RefreshTokenService service = serviceAt(NOW);

        String first = service.issue(1L).value();
        String second = service.issue(1L).value();

        assertThat(first).isNotEqualTo(second).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void hashIsSha256InHex() {
        // well-known SHA-256 of "abc"
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void findsAnIssuedTokenByItsRawValue() {
        RefreshTokenService service = serviceAt(NOW);
        String value = service.issue(9L).value();

        assertThat(service.findActive(value)).get().extracting(RefreshToken::memberId).isEqualTo(9L);
    }

    @Test
    void doesNotFindAnUnknownToken() {
        assertThat(serviceAt(NOW).findActive("unknown")).isEmpty();
    }

    @Test
    void doesNotFindNullOrBlank() {
        RefreshTokenService service = serviceAt(NOW);

        assertThat(service.findActive(null)).isEmpty();
        assertThat(service.findActive("  ")).isEmpty();
    }

    @Test
    void doesNotFindAnExpiredTokenButFindsItUpToTheMomentItExpires() {
        String value = serviceAt(NOW).issue(1L).value();

        assertThat(serviceAt(NOW.plus(Duration.ofDays(7))).findActive(value)).isPresent();
        assertThat(serviceAt(NOW.plus(Duration.ofDays(7)).plusSeconds(1)).findActive(value)).isEmpty();
    }

    @Test
    void doesNotFindARevokedToken() {
        String value = serviceAt(NOW).issue(1L).value();
        RefreshToken stored = repository.saved.get(0);
        repository.saved.set(0, new RefreshToken(stored.id(), stored.memberId(), stored.tokenHash(),
                stored.expiresAt(), NOW.plusSeconds(1)));

        assertThat(serviceAt(NOW.plusSeconds(2)).findActive(value)).isEmpty();
    }

    @Test
    void issuedTokenToStringDoesNotShowTheValue() {
        IssuedRefreshToken issued = serviceAt(NOW).issue(1L);

        assertThat(issued.toString()).doesNotContain(issued.value());
    }

    private static class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

        final List<RefreshToken> saved = new ArrayList<>();

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
    }
}
