package com.englishprep.api.identity.api;

import com.englishprep.api.identity.domain.User;
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

    static RegisterResponse from(User user) {
        return new RegisterResponse(user.id(), user.firstName(), user.lastName(), user.userName(),
                user.email(), user.timeZone(), user.createdAt());
    }
}
