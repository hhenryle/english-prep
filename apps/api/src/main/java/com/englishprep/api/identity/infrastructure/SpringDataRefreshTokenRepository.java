package com.englishprep.api.identity.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
