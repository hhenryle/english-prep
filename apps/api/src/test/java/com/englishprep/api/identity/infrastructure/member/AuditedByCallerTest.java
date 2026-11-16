package com.englishprep.api.identity.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.infrastructure.token.JpaRefreshTokenRepository;
import com.englishprep.api.shared.persistence.JpaAuditingConfig;
import com.englishprep.api.shared.security.AuthenticatedMember;
import jakarta.persistence.EntityManager;
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

/**
 * created_by / updated_by with a real database: they follow the caller in the security context.
 * Auditing runs when the row is written (insert on save, update on flush), so each test sets the
 * caller right before the write it is checking.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaMemberRepository.class,
        JpaRefreshTokenRepository.class})
class AuditedByCallerTest {

    @Autowired
    private JpaMemberRepository members;

    @Autowired
    private JpaRefreshTokenRepository refreshTokens;

    @Autowired
    private EntityManager em;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void callerIs(long memberId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedMember(memberId), null, List.of()));
    }

    private static void noCaller() {
        SecurityContextHolder.clearContext();
    }

    private Member register(String userName) {
        return members.save(Member.register("Alice", "Nguyen", userName, userName + "@example.com", "hash", "UTC"));
    }

    /** Reads created_by and updated_by straight from the table, bypassing JPA. */
    private long[] auditOf(String table, String idColumn, long id) {
        em.flush();
        em.clear();
        Object[] row = (Object[]) em.createNativeQuery(
                        "select created_by, updated_by from " + table + " where " + idColumn + " = ?1")
                .setParameter(1, id).getSingleResult();
        return new long[] {((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    @Test
    void anAnonymousInsertIsRecordedAsTheSystem() {
        noCaller();
        Member member = register("alice");

        assertThat(auditOf("person", "person_id", member.id())).containsExactly(0L, 0L);
    }

    @Test
    void anInsertByALoggedInMemberRecordsThatMemberForBothColumns() {
        callerIs(7L);
        Member member = register("alice");

        assertThat(auditOf("person", "person_id", member.id())).containsExactly(7L, 7L);
    }

    @Test
    void anUpdateChangesUpdatedByAndKeepsCreatedBy() {
        callerIs(7L);
        Member created = register("alice");
        em.flush();
        em.clear();

        callerIs(9L);
        members.save(new Member(created.id(), "Alicia", created.lastName(), created.userName(), created.email(),
                created.passwordHash(), created.timeZone(), created.createdAt(), created.updatedAt()));

        assertThat(auditOf("person", "person_id", created.id())).containsExactly(7L, 9L);
    }

    @Test
    void revokingARefreshTokenIsRecordedAsTheMemberWhoDidIt() {
        noCaller(); // the token is created during login, before there is a caller
        Member member = register("alice");
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        RefreshToken token = refreshTokens.save(RefreshToken.issue(member.id(), "a".repeat(64), expiresAt));
        em.flush();
        em.clear();

        callerIs(member.id()); // the logout request
        refreshTokens.save(new RefreshToken(token.id(), member.id(), token.tokenHash(), expiresAt,
                Instant.now().truncatedTo(ChronoUnit.MICROS)));

        assertThat(auditOf("refresh_token", "refresh_token_id", token.id())).containsExactly(0L, member.id());
    }
}
