package com.gachi.be.domain.notification.entity;

import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** 푸시 수신 실패에도 앱이 다시 조회할 수 있는 사용자별 알림 보관함 엔티티. */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private NotificationType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationLevel level;

  @Column(name = "child_id")
  private Long childId;

  @Column(name = "child_name", length = 50)
  private String childName;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "payload_json", columnDefinition = "TEXT")
  private String payloadJson;

  @Column(name = "template_key", length = 80)
  private String templateKey;

  @Column(name = "template_params_json", columnDefinition = "TEXT")
  private String templateParamsJson;

  @Column(name = "dedupe_key", length = 255)
  private String dedupeKey;

  @Column(name = "read_at")
  private OffsetDateTime readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public Notification(
      Long userId,
      NotificationType type,
      NotificationLevel level,
      Long childId,
      String childName,
      String title,
      String body,
      String payloadJson,
      String templateKey,
      String templateParamsJson,
      String dedupeKey) {
    this.userId = userId;
    this.type = type;
    this.level = level != null ? level : NotificationLevel.IMPORTANT;
    this.childId = childId;
    this.childName = childName;
    this.title = title;
    this.body = body;
    this.payloadJson = payloadJson;
    this.templateKey = templateKey;
    this.templateParamsJson = templateParamsJson;
    this.dedupeKey = dedupeKey;
  }

  public boolean isRead() {
    return readAt != null;
  }

  public void markRead() {
    if (readAt == null) {
      readAt = OffsetDateTime.now();
    }
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (level == null) {
      level = NotificationLevel.IMPORTANT;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
