package com.englishprep.api.identity.application;

/** Input of {@link RegisterUserUseCase}. {@code timeZone} may be null, then UTC is used. */
public record RegisterUserCommand(
        String firstName,
        String lastName,
        String userName,
        String email,
        String password,
        String timeZone) {

    /** Keeps the raw password out of logs and error messages. */
    @Override
    public String toString() {
        return "RegisterUserCommand[userName=" + userName + ", email=" + email + ", password=***]";
    }
}
