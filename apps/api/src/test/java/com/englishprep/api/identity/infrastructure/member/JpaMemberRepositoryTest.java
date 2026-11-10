package com.englishprep.api.identity.infrastructure.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.shared.persistence.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaMemberRepository.class})
class JpaMemberRepositoryTest {

    @Autowired
    private JpaMemberRepository repository;

    @Autowired
    private SpringDataMemberRepository jpa;

    @Autowired
    private EntityManager em;

    private static Member newMember(String userName, String email) {
        return Member.register("Alice", "Nguyen", userName, email, "hash", "Asia/Ho_Chi_Minh");
    }

    /** Sends pending SQL to the database and empties the cache, so the next read hits the database. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Object[] personRow(Long id) {
        return (Object[]) em.createNativeQuery(
                        "select person_type, created_by, updated_by, exclusion_cd from person where person_id = ?1")
                .setParameter(1, id).getSingleResult();
    }

    private long count(String table) {
        return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
    }

    @Test
    void idsAreSequentialNumbers() {
        Member first = repository.save(newMember("alice", "alice@example.com"));
        Member second = repository.save(newMember("bob", "bob@example.com"));

        assertThat(first.id()).isPositive();
        assertThat(second.id()).isEqualTo(first.id() + 1);
    }

    @Test
    void savesAndReadsBackAllFields() {
        Member saved = repository.save(newMember("alice", "alice@example.com"));
        flushAndClear();

        Member found = repository.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.firstName()).isEqualTo("Alice");
        assertThat(found.lastName()).isEqualTo("Nguyen");
        assertThat(found.userName()).isEqualTo("alice");
        assertThat(found.email()).isEqualTo("alice@example.com");
        assertThat(found.passwordHash()).isEqualTo("hash");
        assertThat(found.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void auditColumnsAreFilledAutomatically() {
        Member saved = repository.save(newMember("alice", "alice@example.com"));
        flushAndClear();

        Member found = repository.findById(saved.id()).orElseThrow();
        Object[] row = personRow(saved.id());

        assertThat(found.createdAt()).isNotNull();
        assertThat(found.updatedAt()).isEqualTo(found.createdAt());
        assertThat(row[0]).isEqualTo("MEMBER");
        assertThat(((Number) row[1]).longValue()).isZero();
        assertThat(((Number) row[2]).longValue()).isZero();
        assertThat(row[3]).isEqualTo("IN");
    }

    @Test
    void updateChangesUpdatedAtAndKeepsCreatedAt() throws InterruptedException {
        Member saved = repository.save(newMember("alice", "alice@example.com"));
        flushAndClear();
        Member before = repository.findById(saved.id()).orElseThrow();
        Thread.sleep(10);

        repository.save(new Member(before.id(), before.firstName(), before.lastName(), before.userName(),
                before.email(), before.passwordHash(), "UTC", null, null));
        flushAndClear();
        Member after = repository.findById(saved.id()).orElseThrow();

        assertThat(after.timeZone()).isEqualTo("UTC");
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
    }

    @Test
    void savingAUserWritesOneRowInEachTable() {
        repository.save(newMember("alice", "alice@example.com"));
        flushAndClear();

        assertThat(count("person")).isEqualTo(1);
        assertThat(count("member")).isEqualTo(1);
    }

    @Test
    void findsByEmailAndUserNameIgnoringCase() {
        repository.save(newMember("bob", "bob@example.com"));

        assertThat(repository.findByEmail("BOB@Example.com")).isPresent();
        assertThat(repository.findByUserName("BoB")).isPresent();
        assertThat(repository.existsByEmail("Bob@EXAMPLE.com")).isTrue();
        assertThat(repository.existsByUserName("BOB")).isTrue();
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
        assertThat(repository.existsByUserName("nobody")).isFalse();
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(999_999L)).isEmpty();
    }

    @Test
    void databaseRejectsEmailAndUserNameThatDifferOnlyByCase() {
        repository.save(newMember("carol", "carol@example.com"));
        // Built directly to skip the domain's lower-casing: the index is the last line of defence.
        MemberEntity sameEmail = new MemberEntity("C", "D", "other", "CAROL@example.com", "hash", "UTC");
        assertThatThrownBy(() -> jpa.saveAndFlush(sameEmail))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUserNameThatDiffersOnlyByCase() {
        repository.save(newMember("carol", "carol@example.com"));
        MemberEntity sameUserName = new MemberEntity("C", "D", "CAROL", "other@example.com", "hash", "UTC");
        assertThatThrownBy(() -> jpa.saveAndFlush(sameUserName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excludedUserIsHiddenAndDoesNotBlockTheSameEmail() {
        Member saved = repository.save(newMember("dave", "dave@example.com"));
        flushAndClear();

        em.createNativeQuery("update person set exclusion_cd = 'EX' where person_id = ?1")
                .setParameter(1, saved.id()).executeUpdate();
        em.clear();

        assertThat(repository.findById(saved.id())).isEmpty();
        assertThat(repository.findByEmail("dave@example.com")).isEmpty();
        assertThat(repository.existsByUserName("dave")).isFalse();

        Member again = repository.save(newMember("dave", "dave@example.com"));
        flushAndClear();
        assertThat(again.id()).isNotEqualTo(saved.id());
        assertThat(repository.findByEmail("dave@example.com")).isPresent();
    }

    @Test
    void databaseRejectsUnknownExclusionCode() {
        Member saved = repository.save(newMember("erin", "erin@example.com"));
        flushAndClear();

        assertThatThrownBy(() -> em.createNativeQuery(
                        "update person set exclusion_cd = 'XX' where person_id = ?1")
                .setParameter(1, saved.id()).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }
}
