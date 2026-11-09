package com.englishprep.api.identity.application;

/** Login failed. One message for every reason, so the caller cannot tell which accounts exist. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid login or password");
    }
}
