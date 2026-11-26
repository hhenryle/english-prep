package com.englishprep.api.identity.application.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test with a repository that holds one member (id 7). */
class ChangeTimeZoneUseCaseTest {

    private Member stored = new Member(7L, "Alice", "Nguyen", "alice01", "alice@example.com", "hash", "UTC",
            Instant.now(), Instant.now());

    private final MemberRepository members = new MemberRepository() {
        @Override
        public Member save(Member member) {
            stored = member;
            return member;
        }

        @Override
        public Optional<Member> findById(Long id) {
            return id == 7L ? Optional.of(stored) : Optional.empty();
        }

        @Override
        public Optional<Member> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<Member> findByUserName(String userName) {
            return Optional.empty();
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public boolean existsByUserName(String userName) {
            return false;
        }
    };
    private final ChangeTimeZoneUseCase useCase = new ChangeTimeZoneUseCase(members);

    @Test
    void savesAndReturnsTheMemberWithTheNewZone() {
        Member result = useCase.change(7L, "Asia/Ho_Chi_Minh");

        assertThat(result.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(stored.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(stored.userName()).isEqualTo("alice01");
    }

    @Test
    void trimsTheZone() {
        assertThat(useCase.change(7L, " Europe/Paris ").timeZone()).isEqualTo("Europe/Paris");
    }

    @Test
    void failsWhenTheMemberDoesNotExistAnymore() {
        assertThatThrownBy(() -> useCase.change(99L, "UTC")).isInstanceOf(CurrentMemberNotFoundException.class);
    }

    @Test
    void rejectsAnInvalidZoneAndChangesNothing() {
        assertThatThrownBy(() -> useCase.change(7L, "Mars/Olympus")).isInstanceOf(IllegalArgumentException.class);

        assertThat(stored.timeZone()).isEqualTo("UTC");
    }
}
