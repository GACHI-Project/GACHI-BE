package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AuthRefreshToken> findByJtiAndTokenHash(String jti, String tokenHash);

  @Query("select t.user.id from AuthRefreshToken t where t.jti = :jti and t.tokenHash = :tokenHash")
  Optional<Long> findUserIdByJtiAndTokenHash(
      @Param("jti") String jti, @Param("tokenHash") String tokenHash);

  List<AuthRefreshToken> findAllByUserIdAndRevokedAtIsNull(Long userId);

  long deleteByRevokedAtIsNotNullAndUpdatedAtBefore(OffsetDateTime threshold);
}
