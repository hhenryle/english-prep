package com.englishprep.api.identity.infrastructure;

import com.englishprep.api.identity.domain.Member;
import com.englishprep.api.identity.domain.MemberRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaMemberRepository implements MemberRepository {

    private final SpringDataMemberRepository jpa;

    JpaMemberRepository(SpringDataMemberRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Member save(Member member) {
        MemberEntity entity;
        if (member.id() == null) {
            entity = new MemberEntity(member.firstName(), member.lastName(), member.userName(), member.email(),
                    member.passwordHash(), member.timeZone());
        } else {
            // Load the existing row and change it, so the audit columns (created_at...) are kept.
            entity = jpa.findById(member.id())
                    .orElseThrow(() -> new IllegalStateException("Member not found: " + member.id()));
            entity.update(member.firstName(), member.lastName(), member.userName(), member.email(),
                    member.passwordHash(), member.timeZone());
        }
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Member> findById(Long id) {
        return jpa.findById(id).map(JpaMemberRepository::toDomain);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(JpaMemberRepository::toDomain);
    }

    @Override
    public Optional<Member> findByUserName(String userName) {
        return jpa.findByUserNameIgnoreCase(userName).map(JpaMemberRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUserName(String userName) {
        return jpa.existsByUserNameIgnoreCase(userName);
    }

    private static Member toDomain(MemberEntity e) {
        return new Member(e.getId(), e.getFirstName(), e.getLastName(), e.getUserName(), e.getEmail(),
                e.getPasswordHash(), e.getTimeZone(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
