package com.englishprep.api.identity.infrastructure.token;

import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.domain.token.RefreshTokenRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;
    private final EntityManager entityManager;

    JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity;
        if (token.id() == null) {
            entity = new RefreshTokenEntity(token.memberId(), token.tokenHash(), token.expiresAt(), token.revokedAt());
        } else {
            // Load the existing row and change it, so the audit columns (created_at...) are kept.
            entity = jpa.findById(token.id())
                    .orElseThrow(() -> new IllegalStateException("Refresh token not found: " + token.id()));
            entity.updateRevokedAt(token.revokedAt());
        }
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(JpaRefreshTokenRepository::toDomain);
    }

    /**
     * Locks the row first, so two requests with the same token cannot both see it as active. The change
     * is flushed here (not at commit) so that the audit columns are filled while the caller is known.
     *
     * <p>The row is then re-read from the database. If the same transaction already loaded this token
     * (for example the refresh use case calls find() first), Hibernate would hand back that old copy even
     * after waiting for the lock, and a request that lost the race would still see the token as active.
     */
    @Override
    public boolean revokeIfActive(String tokenHash, Instant now) {
        return jpa.findByTokenHashForUpdate(tokenHash)
                .map(entity -> {
                    entityManager.refresh(entity);
                    return entity;
                })
                .filter(entity -> entity.getRevokedAt() == null && !now.isAfter(entity.getExpiresAt()))
                .map(entity -> {
                    entity.updateRevokedAt(now);
                    jpa.saveAndFlush(entity);
                    return true;
                })
                .orElse(false);
    }

    /** Loads the tokens and changes them one by one (not a bulk SQL update) so the audit columns are kept up to date. */
    @Override
    public int revokeAllOfMember(long memberId, Instant now) {
        List<RefreshTokenEntity> open = jpa.findByMemberIdAndRevokedAtIsNull(memberId);
        open.forEach(entity -> entity.updateRevokedAt(now));
        jpa.saveAllAndFlush(open);
        return open.size();
    }

    private static RefreshToken toDomain(RefreshTokenEntity e) {
        return new RefreshToken(e.getId(), e.getMemberId(), e.getTokenHash(), e.getExpiresAt(), e.getRevokedAt());
    }
}
