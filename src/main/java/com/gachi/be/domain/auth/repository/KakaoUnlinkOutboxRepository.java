package com.gachi.be.domain.auth.repository;

import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KakaoUnlinkOutboxRepository extends JpaRepository<KakaoUnlinkOutbox, Long> {
  boolean existsByProviderUserIdAndProcessedAtIsNull(String providerUserId);

  Optional<KakaoUnlinkOutbox> findByProviderUserIdAndProcessedAtIsNull(String providerUserId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select event from KakaoUnlinkOutbox event where event.providerUserId = :providerUserId and event.processedAt is null")
  Optional<KakaoUnlinkOutbox> findPendingByProviderUserIdForUpdate(
      @Param("providerUserId") String providerUserId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO kakao_unlink_outbox
            (user_id, provider_user_id, event_type, next_attempt_at, created_at, updated_at)
          VALUES (:userId, :providerUserId, 'WEBHOOK_VERIFY', CURRENT_TIMESTAMP,
                  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          ON CONFLICT (provider_user_id) WHERE processed_at IS NULL DO NOTHING
          """,
      nativeQuery = true)
  void enqueueWebhookVerificationIfAbsent(
      @Param("userId") Long userId, @Param("providerUserId") String providerUserId);

  List<KakaoUnlinkOutbox> findTop20ByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByIdAsc(
      OffsetDateTime now);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select event from KakaoUnlinkOutbox event where event.id = :id")
  Optional<KakaoUnlinkOutbox> findByIdForUpdate(@Param("id") Long id);
}
