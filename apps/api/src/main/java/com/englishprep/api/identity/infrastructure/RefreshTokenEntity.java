package com.englishprep.api.identity.infrastructure;

import com.englishprep.api.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

/** Table "refresh_token". Audit columns come from {@link AuditableEntity}; rows with exclusion_cd = 'EX' are hidden. */
@Entity
@Table(name = "refresh_token")
@SQLRestriction("exclusion_cd = 'IN'")
class RefreshTokenEntity extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    /** Plain column, not a relation: the domain only needs the id, and the foreign key is in the database. */
    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenEntity() {
        // required by JPA
    }

    RefreshTokenEntity(Long memberId, String tokenHash, Instant expiresAt, Instant revokedAt) {
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    /** The only column that changes after the token is created. */
    void updateRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    Long getId() {
        return id;
    }

    Long getMemberId() {
        return memberId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }
}
