package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AuthRefreshToken> findByJtiAndTokenHash(String jti, String tokenHash);

  long deleteByRevokedAtIsNotNullAndUpdatedAtBefore(OffsetDateTime threshold);
}
