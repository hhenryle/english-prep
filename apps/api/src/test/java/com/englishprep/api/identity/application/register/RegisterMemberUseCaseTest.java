package com.englishprep.api.identity.application.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.application.password.PasswordHasher;
import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test: the use case runs against an in-memory repository and a fake hasher. */
class RegisterMemberUseCaseTest {

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
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
    private final RegisterMemberUseCase useCase = new RegisterMemberUseCase(members, hasher);

    private static RegisterMemberCommand command(String userName, String email, String timeZone) {
        return new RegisterMemberCommand("Alice", "Nguyen", userName, email, "secret123", timeZone);
    }

    @Test
    void savesTheUserWithAHashedPasswordAndAnId() {
        Member member = useCase.register(command("alice", "alice@example.com", "Asia/Ho_Chi_Minh"));

        assertThat(member.id()).isNotNull();
        assertThat(member.passwordHash()).isEqualTo("hashed:secret123").isNotEqualTo("secret123");
        assertThat(member.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(members.saved).hasSize(1);
    }

    @Test
    void normalizesEmailAndUserName() {
        Member member = useCase.register(command("  Alice01 ", "  Alice@Example.COM ", "UTC"));

        assertThat(member.userName()).isEqualTo("alice01");
        assertThat(member.email()).isEqualTo("alice@example.com");
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
        assertThat(members.saved).hasSize(1);
    }

    @Test
    void refusesAUserNameThatIsAlreadyUsedIgnoringCase() {
        useCase.register(command("alice", "alice@example.com", null));

        assertThatThrownBy(() -> useCase.register(command("ALICE", "other@example.com", null)))
                .isInstanceOf(UserNameAlreadyUsedException.class);
        assertThat(members.saved).hasSize(1);
    }

    @Test
    void commandToStringDoesNotShowThePassword() {
        assertThat(command("alice", "alice@example.com", null).toString()).doesNotContain("secret123");
    }

    private static class InMemoryMemberRepository implements MemberRepository {

        final List<Member> saved = new ArrayList<>();

        @Override
        public Member save(Member member) {
            Member stored = new Member((long) saved.size() + 1, member.firstName(), member.lastName(), member.userName(),
                    member.email(), member.passwordHash(), member.timeZone(), Instant.now(), Instant.now());
            saved.add(stored);
            return stored;
        }

        @Override
        public Optional<Member> findById(Long id) {
            return saved.stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Member> findByEmail(String email) {
            return saved.stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<Member> findByUserName(String userName) {
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
