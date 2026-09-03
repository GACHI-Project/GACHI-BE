package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkOutboxRepository extends JpaRepository<KakaoUnlinkOutbox, Long> {
  boolean existsByProviderUserIdAndProcessedAtIsNull(String providerUserId);

  Optional<KakaoUnlinkOutbox> findByProviderUserIdAndProcessedAtIsNull(String providerUserId);

  List<KakaoUnlinkOutbox> findTop20ByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByIdAsc(
      OffsetDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select event from KakaoUnlinkOutbox event where event.id = :id")
  Optional<KakaoUnlinkOutbox> findByIdForUpdate(@Param("id") Long id);
}
