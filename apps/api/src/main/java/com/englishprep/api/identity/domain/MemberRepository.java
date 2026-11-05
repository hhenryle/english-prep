package com.englishprep.api.identity.domain;

import java.util.Optional;

public interface MemberRepository {

    /** Inserts when the member has no id, otherwise updates the existing one. Returns the saved member. */
    Member save(Member member);

    Optional<Member> findById(Long id);

    Optional<Member> findByEmail(String email);

    Optional<Member> findByUserName(String userName);

    boolean existsByEmail(String email);

    boolean existsByUserName(String userName);
}
