package com.englishprep.api.identity.application.password;

/** Port for hashing passwords, so the use cases do not depend on a specific algorithm. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
