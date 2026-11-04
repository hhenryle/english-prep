package com.englishprep.api.identity.infrastructure;

import com.englishprep.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Table "person", the base of the hierarchy. Each subclass has its own table (JOINED) and its
 * kind is stored in person_type. Rows with exclusion_cd = 'EX' are hidden from every query.
 */
@Entity
@Table(name = "person")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "person_type", length = 20)
@SQLRestriction("exclusion_cd = 'IN'")
abstract class PersonEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "person_id")
    private Long id;

    @Column(name = "first_nm", nullable = false)
    private String firstName;

    @Column(name = "last_nm", nullable = false)
    private String lastName;

    @Column(name = "user_nm", nullable = false)
    private String userName;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected PersonEntity() {
        // required by JPA
    }

    protected PersonEntity(String firstName, String lastName, String userName, String email,
                           String passwordHash) {
        update(firstName, lastName, userName, email, passwordHash);
    }

    protected void update(String firstName, String lastName, String userName, String email,
                          String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.userName = userName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    Long getId() {
        return id;
    }

    String getFirstName() {
        return firstName;
    }

    String getLastName() {
        return lastName;
    }

    String getUserName() {
        return userName;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }
}
