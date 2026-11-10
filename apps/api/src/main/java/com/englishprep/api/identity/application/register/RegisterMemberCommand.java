package com.englishprep.api.identity.application.register;

/** Input of {@link RegisterMemberUseCase}. {@code timeZone} may be null, then UTC is used. */
public record RegisterMemberCommand(
        String firstName,
        String lastName,
        String userName,
        String email,
        String password,
        String timeZone) {

    /** Keeps the raw password out of logs and error messages. */
    @Override
    public String toString() {
        return "RegisterMemberCommand[userName=" + userName + ", email=" + email + ", password=***]";
    }
}
