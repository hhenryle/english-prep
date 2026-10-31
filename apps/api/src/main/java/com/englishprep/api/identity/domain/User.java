package com.englishprep.api.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String timeZone;
    private final Instant createdAt;
    private final Instant updatedAt;

    public User(UUID id, String email, String passwordHash, String timeZone,
                Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = requireText(email, "email");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.timeZone = requireText(timeZone, "timeZone");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User register(String email, String passwordHash, String timeZone) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), normalizeEmail(email), passwordHash, timeZone, now, now);
    }

    /** Emails are compared ignoring case, so they are stored trimmed and lower-cased. */
    public static String normalizeEmail(String email) {
        return requireText(email, "email").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String timeZone() {
        return timeZone;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
