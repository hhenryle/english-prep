package com.englishprep.api.identity.api.login;

import com.englishprep.api.identity.application.login.LoginResult;
import com.englishprep.api.identity.domain.member.Member;
import java.time.Instant;

/** A successful login: both tokens, when they expire, and the member. Never contains the password or its hash. */
public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        MemberSummary member) {

    /** The member fields the app needs right after login. */
    public record MemberSummary(Long id, String firstName, String lastName, String userName, String email,
                                String timeZone) {

        static MemberSummary from(Member member) {
            return new MemberSummary(member.id(), member.firstName(), member.lastName(), member.userName(),
                    member.email(), member.timeZone());
        }
    }

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse("Bearer", result.accessToken().value(), result.accessToken().expiresAt(),
                result.refreshToken().value(), result.refreshToken().expiresAt(),
                MemberSummary.from(result.member()));
    }
}
