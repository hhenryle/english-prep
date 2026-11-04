package com.englishprep.api.identity.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * What every kind of person has in common. A {@link User} is one kind; other kinds
 * (for example an admin) can extend this class later.
 * {@code id}, {@code createdAt} and {@code updatedAt} are null until the person is saved.
 */
public abstract class Person {

    private final Long id;
    private final String firstName;
    private final String lastName;
    private final String userName;
    private final String email;
    private final String passwordHash;
    private final Instant createdAt;
    private final Instant updatedAt;

    protected Person(Long id, String firstName, String lastName, String userName, String email,
                     String passwordHash, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        this.userName = requireText(userName, "userName");
        this.email = requireText(email, "email");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Email and user name are compared ignoring case, so they are stored trimmed and lower-cased. */
    public static String normalizeLogin(String value, String name) {
        return requireText(value, name).toLowerCase(Locale.ROOT);
    }

    protected static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    public Long id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String userName() {
        return userName;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
