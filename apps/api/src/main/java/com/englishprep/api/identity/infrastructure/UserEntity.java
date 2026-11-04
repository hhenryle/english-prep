package com.englishprep.api.identity.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Table "app_user": the extra columns of a person whose person_type is USER. */
@Entity
@Table(name = "app_user")
@DiscriminatorValue("USER")
@PrimaryKeyJoinColumn(name = "person_id")
class UserEntity extends PersonEntity {

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    protected UserEntity() {
        // required by JPA
    }

    UserEntity(String firstName, String lastName, String userName, String email,
               String passwordHash, String timeZone) {
        super(firstName, lastName, userName, email, passwordHash);
        this.timeZone = timeZone;
    }

    void update(String firstName, String lastName, String userName, String email,
                String passwordHash, String timeZone) {
        super.update(firstName, lastName, userName, email, passwordHash);
        this.timeZone = timeZone;
    }

    String getTimeZone() {
        return timeZone;
    }
}
