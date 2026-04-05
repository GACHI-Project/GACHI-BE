package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
  Optional<AuthRefreshToken> findByJtiAndTokenHash(String jti, String tokenHash);

  long deleteByRevokedAtIsNotNullAndUpdatedAtBefore(OffsetDateTime threshold);
}
