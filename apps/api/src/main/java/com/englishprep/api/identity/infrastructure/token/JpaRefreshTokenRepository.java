package com.englishprep.api.identity.infrastructure.token;

import com.englishprep.api.identity.domain.token.RefreshToken;
import com.englishprep.api.identity.domain.token.RefreshTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
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

    private static RefreshToken toDomain(RefreshTokenEntity e) {
        return new RefreshToken(e.getId(), e.getMemberId(), e.getTokenHash(), e.getExpiresAt(), e.getRevokedAt());
    }
}
