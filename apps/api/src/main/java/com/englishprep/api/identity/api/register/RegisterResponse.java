package com.englishprep.api.identity.api.register;

import com.englishprep.api.identity.domain.member.Member;
import java.time.Instant;

/** The created account. Never contains the password or its hash. */
public record RegisterResponse(
        Long id,
        String firstName,
        String lastName,
        String userName,
        String email,
        String timeZone,
        Instant createdAt) {

    public static RegisterResponse from(Member member) {
        return new RegisterResponse(member.id(), member.firstName(), member.lastName(), member.userName(),
                member.email(), member.timeZone(), member.createdAt());
    }
}
