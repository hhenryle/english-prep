package com.englishprep.api.identity.application.me;

/**
 * The token is valid but its member no longer exists (for example the account was deleted after the
 * token was issued). For the caller this means "not authenticated", so it is answered with a 401.
 */
public class CurrentMemberNotFoundException extends RuntimeException {

    public CurrentMemberNotFoundException() {
        super("The member of this token no longer exists");
    }
}
