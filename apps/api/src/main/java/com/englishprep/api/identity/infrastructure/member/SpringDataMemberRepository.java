package com.englishprep.api.identity.infrastructure.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMemberRepository extends JpaRepository<MemberEntity, Long> {

    Optional<MemberEntity> findByEmailIgnoreCase(String email);

    Optional<MemberEntity> findByUserNameIgnoreCase(String userName);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUserNameIgnoreCase(String userName);
}
