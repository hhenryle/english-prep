package com.englishprep.api.identity.application.register;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("Email is already registered");
    }
}
