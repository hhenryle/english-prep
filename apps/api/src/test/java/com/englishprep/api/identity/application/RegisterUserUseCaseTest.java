package com.englishprep.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.domain.User;
import com.englishprep.api.identity.domain.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test: the use case runs against an in-memory repository and a fake hasher. */
class RegisterUserUseCaseTest {

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return hash(rawPassword).equals(passwordHash);
        }
    };
    private final RegisterUserUseCase useCase = new RegisterUserUseCase(users, hasher);

    private static RegisterUserCommand command(String userName, String email, String timeZone) {
        return new RegisterUserCommand("Alice", "Nguyen", userName, email, "secret123", timeZone);
    }

    @Test
    void savesTheUserWithAHashedPasswordAndAnId() {
        User user = useCase.register(command("alice", "alice@example.com", "Asia/Ho_Chi_Minh"));

        assertThat(user.id()).isNotNull();
        assertThat(user.passwordHash()).isEqualTo("hashed:secret123").isNotEqualTo("secret123");
        assertThat(user.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(users.saved).hasSize(1);
    }

    @Test
    void normalizesEmailAndUserName() {
        User user = useCase.register(command("  Alice01 ", "  Alice@Example.COM ", "UTC"));

        assertThat(user.userName()).isEqualTo("alice01");
        assertThat(user.email()).isEqualTo("alice@example.com");
    }

    @Test
    void usesUtcWhenNoTimeZoneIsGiven() {
        assertThat(useCase.register(command("a1", "a1@example.com", null)).timeZone()).isEqualTo("UTC");
        assertThat(useCase.register(command("a2", "a2@example.com", "  ")).timeZone()).isEqualTo("UTC");
    }

    @Test
    void refusesAnEmailThatIsAlreadyUsedIgnoringCase() {
        useCase.register(command("alice", "alice@example.com", null));

        assertThatThrownBy(() -> useCase.register(command("other", "ALICE@example.com", null)))
                .isInstanceOf(EmailAlreadyUsedException.class);
        assertThat(users.saved).hasSize(1);
    }

    @Test
    void refusesAUserNameThatIsAlreadyUsedIgnoringCase() {
        useCase.register(command("alice", "alice@example.com", null));

        assertThatThrownBy(() -> useCase.register(command("ALICE", "other@example.com", null)))
                .isInstanceOf(UserNameAlreadyUsedException.class);
        assertThat(users.saved).hasSize(1);
    }

    @Test
    void commandToStringDoesNotShowThePassword() {
        assertThat(command("alice", "alice@example.com", null).toString()).doesNotContain("secret123");
    }

    private static class InMemoryUserRepository implements UserRepository {

        final List<User> saved = new ArrayList<>();

        @Override
        public User save(User user) {
            User stored = new User((long) saved.size() + 1, user.firstName(), user.lastName(), user.userName(),
                    user.email(), user.passwordHash(), user.timeZone(), Instant.now(), Instant.now());
            saved.add(stored);
            return stored;
        }

        @Override
        public Optional<User> findById(Long id) {
            return saved.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return saved.stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<User> findByUserName(String userName) {
            return saved.stream().filter(u -> u.userName().equalsIgnoreCase(userName)).findFirst();
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public boolean existsByUserName(String userName) {
            return findByUserName(userName).isPresent();
        }
    }
}
