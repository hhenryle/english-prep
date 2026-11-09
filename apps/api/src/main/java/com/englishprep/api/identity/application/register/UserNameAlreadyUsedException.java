package com.englishprep.api.identity.application.register;

public class UserNameAlreadyUsedException extends RuntimeException {

    public UserNameAlreadyUsedException() {
        super("User name is already taken");
    }
}
