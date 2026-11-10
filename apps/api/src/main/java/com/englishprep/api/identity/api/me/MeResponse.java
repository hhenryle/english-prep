package com.englishprep.api.identity.api.me;

import com.englishprep.api.identity.domain.member.Member;
import java.time.Instant;

/** The caller's own account. Never contains the password or its hash. */
public record MeResponse(
        Long id,
        String firstName,
        String lastName,
        String userName,
        String email,
        String timeZone,
        Instant createdAt) {

    static MeResponse from(Member member) {
        return new MeResponse(member.id(), member.firstName(), member.lastName(), member.userName(),
                member.email(), member.timeZone(), member.createdAt());
    }
}
