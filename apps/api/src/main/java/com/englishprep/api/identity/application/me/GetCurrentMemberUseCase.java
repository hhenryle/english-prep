package com.englishprep.api.identity.application.me;

import com.englishprep.api.identity.domain.member.Member;
import com.englishprep.api.identity.domain.member.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the member who is calling. The access token only carries the member id, so this is the first
 * place that reads the database: it also notices a member who was deleted after the token was issued.
 */
@Service
public class GetCurrentMemberUseCase {

    private final MemberRepository members;

    public GetCurrentMemberUseCase(MemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public Member get(long memberId) {
        return members.findById(memberId).orElseThrow(CurrentMemberNotFoundException::new);
    }
}
