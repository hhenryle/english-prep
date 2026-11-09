package com.englishprep.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.domain.Member;
import com.englishprep.api.identity.domain.MemberRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test: in-memory repository and a fake hasher that records every comparison. */
class LoginWithPasswordUseCaseTest {

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final RecordingHasher hasher = new RecordingHasher();
    private final LoginWithPasswordUseCase useCase = new LoginWithPasswordUseCase(members, hasher);

    {
        members.add(new Member(1L, "Alice", "Nguyen", "alice01", "alice@example.com", "hashed:secret123", "UTC",
                Instant.now(), Instant.now()));
    }

    @Test
    void logsInWithTheUserName() {
        assertThat(useCase.login(new LoginCommand("alice01", "secret123")).id()).isEqualTo(1L);
    }

    @Test
    void logsInWithTheEmail() {
        assertThat(useCase.login(new LoginCommand("alice@example.com", "secret123")).id()).isEqualTo(1L);
    }

    @Test
    void ignoresCaseAndSurroundingSpacesOfTheLogin() {
        assertThat(useCase.login(new LoginCommand("  ALICE01 ", "secret123")).id()).isEqualTo(1L);
        assertThat(useCase.login(new LoginCommand(" Alice@Example.COM ", "secret123")).id()).isEqualTo(1L);
    }

    @Test
    void thePasswordIsCaseSensitive() {
        assertThatThrownBy(() -> useCase.login(new LoginCommand("alice01", "SECRET123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPasswordAndUnknownAccountFailTheSameWay() {
        Throwable wrongPassword = catchThrowable(new LoginCommand("alice01", "nope"));
        Throwable unknownUserName = catchThrowable(new LoginCommand("nobody", "secret123"));
        Throwable unknownEmail = catchThrowable(new LoginCommand("nobody@example.com", "secret123"));

        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownUserName).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
        assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(wrongPassword.getMessage());
    }

    @Test
    void anUnknownAccountStillRunsOnePasswordComparison() {
        hasher.comparisons.clear();

        catchThrowable(new LoginCommand("nobody", "secret123"));

        assertThat(hasher.comparisons).hasSize(1);
    }

    @Test
    void anUnknownAccountIsNeverAcceptedEvenWithTheDummyPassword() {
        assertThatThrownBy(() -> useCase.login(new LoginCommand("nobody", "not-a-real-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void blankOrMissingInputIsInvalidCredentialsNotAnotherError() {
        assertThatThrownBy(() -> useCase.login(new LoginCommand(null, "secret123")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("   ", "secret123")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("alice01", null)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> useCase.login(new LoginCommand("alice01", "")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void commandToStringDoesNotShowThePassword() {
        assertThat(new LoginCommand("alice01", "secret123").toString()).doesNotContain("secret123");
    }

    private Throwable catchThrowable(LoginCommand command) {
        try {
            useCase.login(command);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static class RecordingHasher implements PasswordHasher {

        final List<String> comparisons = new ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            comparisons.add(rawPassword);
            return hash(rawPassword).equals(passwordHash);
        }
    }

    private static class InMemoryMemberRepository implements MemberRepository {

        private final List<Member> stored = new ArrayList<>();

        void add(Member member) {
            stored.add(member);
        }

        @Override
        public Member save(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Member> findById(Long id) {
            return stored.stream().filter(m -> m.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Member> findByEmail(String email) {
            return stored.stream().filter(m -> m.email().equals(email)).findFirst();
        }

        @Override
        public Optional<Member> findByUserName(String userName) {
            return stored.stream().filter(m -> m.userName().equals(userName)).findFirst();
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
