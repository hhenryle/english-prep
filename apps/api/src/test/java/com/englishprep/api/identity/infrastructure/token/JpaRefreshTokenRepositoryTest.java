package com.englishprep.api.identity.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.infrastructure.member.JpaMemberRepository;
import com.englishprep.api.shared.persistence.JpaAuditingConfig;
import com.englishprep.api.shared.security.AuthenticatedMember;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaMemberRepository.class,
        JpaRefreshTokenRepository.class})
class JpaRefreshTokenRepositoryTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private JpaRefreshTokenRepository repository;

    @Autowired
    private JpaMemberRepository members;

    @Autowired
    private EntityManager em;

    /** The refresh_token table has a foreign key to member, so every test needs a real member first. */
    private long newMemberId() {
        return members.save(Member.register("Alice", "Nguyen", "alice", "alice@example.com", "hash", "UTC")).id();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static Instant inOneWeek() {
        // the database keeps microseconds, so cut the Java value to the same precision
        return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void savesAndReadsBackAllFields() {
        long memberId = newMemberId();
        Instant expiresAt = inOneWeek();
        RefreshToken saved = repository.save(RefreshToken.issue(memberId, HASH, expiresAt));
        flushAndClear();

        RefreshToken found = repository.findByTokenHash(HASH).orElseThrow();

        assertThat(saved.id()).isPositive();
        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.memberId()).isEqualTo(memberId);
        assertThat(found.tokenHash()).isEqualTo(HASH);
        assertThat(found.expiresAt()).isEqualTo(expiresAt);
        assertThat(found.revokedAt()).isNull();
    }

    @Test
    void auditColumnsAreFilledAutomatically() {
        RefreshToken saved = repository.save(RefreshToken.issue(newMemberId(), HASH, inOneWeek()));
        flushAndClear();

        Object[] row = (Object[]) em.createNativeQuery(
                        "select created_by, updated_by, exclusion_cd, created_at from refresh_token where refresh_token_id = ?1")
                .setParameter(1, saved.id()).getSingleResult();

        assertThat(((Number) row[0]).longValue()).isZero();
        assertThat(((Number) row[1]).longValue()).isZero();
        assertThat(row[2]).isEqualTo("IN");
        assertThat(row[3]).isNotNull();
    }

    @Test
    void returnsEmptyForAnUnknownHash() {
        assertThat(repository.findByTokenHash("f".repeat(64))).isEmpty();
    }

    @Test
    void savingAgainPersistsTheRevocationAndKeepsTheOtherFields() {
        long memberId = newMemberId();
        Instant expiresAt = inOneWeek();
        RefreshToken saved = repository.save(RefreshToken.issue(memberId, HASH, expiresAt));
        flushAndClear();
        Instant revokedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        repository.save(new RefreshToken(saved.id(), memberId, HASH, expiresAt, revokedAt));
        flushAndClear();

        RefreshToken found = repository.findByTokenHash(HASH).orElseThrow();
        assertThat(found.revokedAt()).isEqualTo(revokedAt);
        assertThat(found.expiresAt()).isEqualTo(expiresAt);
        assertThat(found.isActive(Instant.now())).isFalse();
    }

    @Test
    void theSameHashCannotBeStoredTwice() {
        long memberId = newMemberId();
        repository.save(RefreshToken.issue(memberId, HASH, inOneWeek()));
        flushAndClear();

        assertThatThrownBy(() -> {
            repository.save(RefreshToken.issue(memberId, HASH, inOneWeek()));
            em.flush();
        }).isInstanceOfAny(PersistenceException.class, org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void aTokenForAnUnknownMemberIsRejectedByTheForeignKey() {
        assertThatThrownBy(() -> {
            repository.save(RefreshToken.issue(999_999L, HASH, inOneWeek()));
            em.flush();
        }).isInstanceOfAny(PersistenceException.class, org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void anExcludedRowIsHidden() {
        RefreshToken saved = repository.save(RefreshToken.issue(newMemberId(), HASH, inOneWeek()));
        flushAndClear();
        em.createNativeQuery("update refresh_token set exclusion_cd = 'EX' where refresh_token_id = ?1")
                .setParameter(1, saved.id()).executeUpdate();
        flushAndClear();

        assertThat(repository.findByTokenHash(HASH)).isEmpty();
    }

    // --- revokeIfActive / revokeAllOfMember ---

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    private long otherMemberId() {
        return members.save(Member.register("Bob", "Tran", "bob", "bob@example.com", "hash", "UTC")).id();
    }

    private String hashOf(char c) {
        return String.valueOf(c).repeat(64);
    }

    @Test
    void revokeIfActiveRevokesOnceAndThenReportsFalse() {
        repository.save(RefreshToken.issue(newMemberId(), HASH, inOneWeek()));
        flushAndClear();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        assertThat(repository.revokeIfActive(HASH, now)).isTrue();
        assertThat(repository.revokeIfActive(HASH, now.plusSeconds(1))).isFalse();

        flushAndClear();
        assertThat(repository.findByTokenHash(HASH).orElseThrow().revokedAt()).isEqualTo(now);
    }

    @Test
    void revokeIfActiveIsFalseForAnUnknownHash() {
        assertThat(repository.revokeIfActive(hashOf('f'), Instant.now())).isFalse();
    }

    @Test
    void revokeIfActiveIsFalseForAnExpiredTokenAndLeavesItAlone() {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        repository.save(RefreshToken.issue(newMemberId(), HASH, expiresAt));
        flushAndClear();

        assertThat(repository.revokeIfActive(HASH, Instant.now())).isFalse();

        flushAndClear();
        assertThat(repository.findByTokenHash(HASH).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void revokeIfActiveStillWorksAtTheExactMomentOfExpiry() {
        Instant expiresAt = inOneWeek();
        repository.save(RefreshToken.issue(newMemberId(), HASH, expiresAt));
        flushAndClear();

        assertThat(repository.revokeIfActive(HASH, expiresAt)).isTrue();
    }

    @Test
    void revokeAllOfMemberRevokesOnlyThatMembersOpenTokens() {
        long alice = newMemberId();
        long bob = otherMemberId();
        Instant earlier = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        repository.save(RefreshToken.issue(alice, hashOf('1'), inOneWeek()));
        repository.save(RefreshToken.issue(alice, hashOf('2'), inOneWeek()));
        repository.save(new RefreshToken(null, alice, hashOf('3'), inOneWeek(), earlier)); // already revoked
        repository.save(RefreshToken.issue(bob, hashOf('4'), inOneWeek()));
        flushAndClear();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        int revoked = repository.revokeAllOfMember(alice, now);

        flushAndClear();
        assertThat(revoked).isEqualTo(2);
        assertThat(repository.findByTokenHash(hashOf('1')).orElseThrow().revokedAt()).isEqualTo(now);
        assertThat(repository.findByTokenHash(hashOf('2')).orElseThrow().revokedAt()).isEqualTo(now);
        assertThat(repository.findByTokenHash(hashOf('3')).orElseThrow().revokedAt()).isEqualTo(earlier);
        assertThat(repository.findByTokenHash(hashOf('4')).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void revokeAllOfMemberReturnsZeroWhenThereIsNothingToRevoke() {
        assertThat(repository.revokeAllOfMember(newMemberId(), Instant.now())).isZero();
    }

    @Test
    void revokingRecordsTheCallerInUpdatedBy() {
        long memberId = newMemberId();
        RefreshToken token = repository.save(RefreshToken.issue(memberId, HASH, inOneWeek()));
        flushAndClear();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken
                .authenticated(new AuthenticatedMember(memberId), null, List.of()));

        repository.revokeIfActive(HASH, Instant.now());

        flushAndClear();
        Object[] row = (Object[]) em.createNativeQuery(
                        "select created_by, updated_by from refresh_token where refresh_token_id = ?1")
                .setParameter(1, token.id()).getSingleResult();
        assertThat(((Number) row[0]).longValue()).isZero();
        assertThat(((Number) row[1]).longValue()).isEqualTo(memberId);
    }
}
