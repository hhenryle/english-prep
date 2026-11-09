package com.englishprep.api.identity.infrastructure.member;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

/** Table "member": the extra columns of a person whose person_type is MEMBER. */
@Entity
@Table(name = "member")
@DiscriminatorValue("MEMBER")
@PrimaryKeyJoinColumn(name = "member_id")
class MemberEntity extends PersonEntity {

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    protected MemberEntity() {
        // required by JPA
    }

    MemberEntity(String firstName, String lastName, String userName, String email,
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
