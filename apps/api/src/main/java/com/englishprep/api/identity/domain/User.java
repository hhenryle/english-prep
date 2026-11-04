package com.englishprep.api.identity.domain;

import java.time.Instant;
import java.time.ZoneId;

public final class User extends Person {

    private final String timeZone;

    public User(Long id, String firstName, String lastName, String userName, String email,
                String passwordHash, String timeZone, Instant createdAt, Instant updatedAt) {
        super(id, firstName, lastName, userName, email, passwordHash, createdAt, updatedAt);
        this.timeZone = requireText(timeZone, "timeZone");
    }

    /** A new user that has not been saved yet: no id and no timestamps, the database assigns them. */
    public static User register(String firstName, String lastName, String userName, String email,
                                String passwordHash, String timeZone) {
        if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone.strip())) {
            throw new IllegalArgumentException("timeZone must be a valid time zone id");
        }
        return new User(null, firstName, lastName,
                normalizeLogin(userName, "userName"), normalizeLogin(email, "email"),
                passwordHash, timeZone, null, null);
    }

    public String timeZone() {
        return timeZone;
    }
}
