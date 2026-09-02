package com.gachi.be.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "kakao_unlink_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoUnlinkOutbox {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "provider_user_id", nullable = false, length = 100)
  private String providerUserId;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private OffsetDateTime nextAttemptAt;

  @Column(name = "processed_at")
  private OffsetDateTime processedAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public KakaoUnlinkOutbox(Long userId, String providerUserId) {
    this.userId = userId;
    this.providerUserId = providerUserId;
    this.nextAttemptAt = OffsetDateTime.now();
  }

  public boolean isProcessed() {
    return processedAt != null;
  }

  public void complete(OffsetDateTime completedAt) {
    this.processedAt = completedAt;
    this.lastError = null;
  }

  public void scheduleRetry(String error, OffsetDateTime retryAt) {
    attempts++;
    lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    nextAttemptAt = retryAt;
  }

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (nextAttemptAt == null) {
      nextAttemptAt = now;
    }
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
