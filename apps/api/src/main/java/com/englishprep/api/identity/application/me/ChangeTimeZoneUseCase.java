package com.englishprep.api.identity.application.me;

import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes the time zone of the logged-in member. Sessions are not affected. */
@Service
public class ChangeTimeZoneUseCase {

    private final MemberRepository members;

    public ChangeTimeZoneUseCase(MemberRepository members) {
        this.members = members;
    }

    /** Reads the member from the database (missing -> 401), saves the copy with the new zone and returns it. */
    @Transactional
    public Member change(long memberId, String timeZone) {
        Member member = members.findById(memberId).orElseThrow(CurrentMemberNotFoundException::new);
        return members.save(member.withTimeZone(timeZone.strip()));
    }
}
