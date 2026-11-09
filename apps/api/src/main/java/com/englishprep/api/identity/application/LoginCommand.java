package com.englishprep.api.identity.application;

/** Input of {@link LoginWithPasswordUseCase}. {@code login} is a user name or an email. */
public record LoginCommand(String login, String password) {

    /** Keeps the raw password out of logs and error messages. */
    @Override
    public String toString() {
        return "LoginCommand[login=" + login + ", password=***]";
    }
}
