package com.englishprep.api.identity.domain.member;

import java.time.Instant;
import java.time.ZoneId;

public final class Member extends Person {

    private final String timeZone;

    public Member(Long id, String firstName, String lastName, String userName, String email,
                String passwordHash, String timeZone, Instant createdAt, Instant updatedAt) {
        super(id, firstName, lastName, userName, email, passwordHash, createdAt, updatedAt);
        this.timeZone = requireText(timeZone, "timeZone");
    }

    /** A new member that has not been saved yet: no id and no timestamps, the database assigns them. */
    public static Member register(String firstName, String lastName, String userName, String email,
                                String passwordHash, String timeZone) {
        if (timeZone == null || !ZoneId.getAvailableZoneIds().contains(timeZone.strip())) {
            throw new IllegalArgumentException("timeZone must be a valid time zone id");
        }
        return new Member(null, firstName, lastName,
                normalizeLogin(userName, "userName"), normalizeLogin(email, "email"),
                passwordHash, timeZone, null, null);
    }

    public String timeZone() {
        return timeZone;
    }
}
