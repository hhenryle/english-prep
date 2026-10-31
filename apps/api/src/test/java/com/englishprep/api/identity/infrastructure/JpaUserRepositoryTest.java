package com.englishprep.api.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.TestcontainersConfiguration;
import com.englishprep.api.identity.domain.User;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaUserRepository.class})
class JpaUserRepositoryTest {

    @Autowired
    private JpaUserRepository repository;

    @Autowired
    private SpringDataUserRepository jpa;

    @Test
    void savesAndReadsBackById() {
        User saved = repository.save(User.register("alice@example.com", "hash", "Asia/Ho_Chi_Minh"));

        User found = repository.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.email()).isEqualTo("alice@example.com");
        assertThat(found.passwordHash()).isEqualTo("hash");
        assertThat(found.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(found.createdAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(saved.createdAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void findsByEmailIgnoringCase() {
        repository.save(User.register("bob@example.com", "hash", "UTC"));

        assertThat(repository.findByEmail("BOB@Example.com")).isPresent();
        assertThat(repository.existsByEmail("Bob@EXAMPLE.com")).isTrue();
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void databaseRejectsEmailThatDiffersOnlyByCase() {
        repository.save(User.register("carol@example.com", "hash", "UTC"));
        // Bypass the domain's lower-casing to prove the DB index is the last line of defence.
        UserEntity duplicate = new UserEntity(UUID.randomUUID(), "CAROL@example.com", "hash", "UTC",
                java.time.Instant.now(), java.time.Instant.now());

        assertThatThrownBy(() -> jpa.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
