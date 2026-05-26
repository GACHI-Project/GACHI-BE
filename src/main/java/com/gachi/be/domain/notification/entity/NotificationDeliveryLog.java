package com.gachi.be.domain.notification.entity;

import com.gachi.be.domain.notification.entity.enums.NotificationDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 푸시 발송 시도별 성공/실패 원인을 남기는 추적 엔티티. */
@Getter
@Entity
@Table(name = "notification_delivery_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDeliveryLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "notification_id", nullable = false)
  private Notification notification;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "push_device_token_id")
  private PushDeviceToken pushDeviceToken;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationDeliveryStatus status;

  @Column(nullable = false, length = 20)
  private String provider;

  @Column(name = "provider_message_id", length = 255)
  private String providerMessageId;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

  @Column(name = "attempted_at", nullable = false)
  private OffsetDateTime attemptedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public NotificationDeliveryLog(
      Notification notification,
      PushDeviceToken pushDeviceToken,
      NotificationDeliveryStatus status,
      String provider,
      String providerMessageId,
      String failureReason) {
    this.notification = notification;
    this.pushDeviceToken = pushDeviceToken;
    this.status = status;
    this.provider = provider != null ? provider : "UNKNOWN";
    this.providerMessageId = providerMessageId;
    this.failureReason = failureReason;
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (attemptedAt == null) {
      attemptedAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
