package com.englishprep.api.identity.application.password;

/** The new password is the same as the current one. */
public class PasswordNotChangedException extends RuntimeException {

    public PasswordNotChangedException() {
        super("The new password must be different from the current password");
    }
}
