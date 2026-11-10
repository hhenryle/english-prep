package com.englishprep.api.identity.application.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Plain unit test with a repository that knows one member (id 7). */
class GetCurrentMemberUseCaseTest {

    private static final Member ALICE = new Member(7L, "Alice", "Nguyen", "alice01", "alice@example.com", "hash",
            "UTC", Instant.now(), Instant.now());

    private final MemberRepository members = new MemberRepository() {
        @Override
        public Member save(Member member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Member> findById(Long id) {
            return id == 7L ? Optional.of(ALICE) : Optional.empty();
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
    private final GetCurrentMemberUseCase useCase = new GetCurrentMemberUseCase(members);

    @Test
    void returnsTheMemberWithThatId() {
        assertThat(useCase.get(7L)).isSameAs(ALICE);
    }

    @Test
    void failsWhenTheMemberDoesNotExistAnymore() {
        assertThatThrownBy(() -> useCase.get(99L)).isInstanceOf(CurrentMemberNotFoundException.class);
    }
}
