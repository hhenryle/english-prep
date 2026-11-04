package com.englishprep.api.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.User;
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
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaUserRepository.class})
class JpaUserRepositoryTest {

    @Autowired
    private JpaUserRepository repository;

    @Autowired
    private SpringDataUserRepository jpa;

    @Autowired
    private EntityManager em;

    private static User newUser(String userName, String email) {
        return User.register("Alice", "Nguyen", userName, email, "hash", "Asia/Ho_Chi_Minh");
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
        User first = repository.save(newUser("alice", "alice@example.com"));
        User second = repository.save(newUser("bob", "bob@example.com"));

        assertThat(first.id()).isPositive();
        assertThat(second.id()).isEqualTo(first.id() + 1);
    }

    @Test
    void savesAndReadsBackAllFields() {
        User saved = repository.save(newUser("alice", "alice@example.com"));
        flushAndClear();

        User found = repository.findById(saved.id()).orElseThrow();

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
        User saved = repository.save(newUser("alice", "alice@example.com"));
        flushAndClear();

        User found = repository.findById(saved.id()).orElseThrow();
        Object[] row = personRow(saved.id());

        assertThat(found.createdAt()).isNotNull();
        assertThat(found.updatedAt()).isEqualTo(found.createdAt());
        assertThat(row[0]).isEqualTo("USER");
        assertThat(((Number) row[1]).longValue()).isZero();
        assertThat(((Number) row[2]).longValue()).isZero();
        assertThat(row[3]).isEqualTo("IN");
    }

    @Test
    void updateChangesUpdatedAtAndKeepsCreatedAt() throws InterruptedException {
        User saved = repository.save(newUser("alice", "alice@example.com"));
        flushAndClear();
        User before = repository.findById(saved.id()).orElseThrow();
        Thread.sleep(10);

        repository.save(new User(before.id(), before.firstName(), before.lastName(), before.userName(),
                before.email(), before.passwordHash(), "UTC", null, null));
        flushAndClear();
        User after = repository.findById(saved.id()).orElseThrow();

        assertThat(after.timeZone()).isEqualTo("UTC");
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfter(before.updatedAt());
    }

    @Test
    void savingAUserWritesOneRowInEachTable() {
        repository.save(newUser("alice", "alice@example.com"));
        flushAndClear();

        assertThat(count("person")).isEqualTo(1);
        assertThat(count("app_user")).isEqualTo(1);
    }

    @Test
    void findsByEmailAndUserNameIgnoringCase() {
        repository.save(newUser("bob", "bob@example.com"));

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
        repository.save(newUser("carol", "carol@example.com"));
        // Built directly to skip the domain's lower-casing: the index is the last line of defence.
        UserEntity sameEmail = new UserEntity("C", "D", "other", "CAROL@example.com", "hash", "UTC");
        assertThatThrownBy(() -> jpa.saveAndFlush(sameEmail))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUserNameThatDiffersOnlyByCase() {
        repository.save(newUser("carol", "carol@example.com"));
        UserEntity sameUserName = new UserEntity("C", "D", "CAROL", "other@example.com", "hash", "UTC");
        assertThatThrownBy(() -> jpa.saveAndFlush(sameUserName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excludedUserIsHiddenAndDoesNotBlockTheSameEmail() {
        User saved = repository.save(newUser("dave", "dave@example.com"));
        flushAndClear();

        em.createNativeQuery("update person set exclusion_cd = 'EX' where person_id = ?1")
                .setParameter(1, saved.id()).executeUpdate();
        em.clear();

        assertThat(repository.findById(saved.id())).isEmpty();
        assertThat(repository.findByEmail("dave@example.com")).isEmpty();
        assertThat(repository.existsByUserName("dave")).isFalse();

        User again = repository.save(newUser("dave", "dave@example.com"));
        flushAndClear();
        assertThat(again.id()).isNotEqualTo(saved.id());
        assertThat(repository.findByEmail("dave@example.com")).isPresent();
    }

    @Test
    void databaseRejectsUnknownExclusionCode() {
        User saved = repository.save(newUser("erin", "erin@example.com"));
        flushAndClear();

        assertThatThrownBy(() -> em.createNativeQuery(
                        "update person set exclusion_cd = 'XX' where person_id = ?1")
                .setParameter(1, saved.id()).executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }
}
