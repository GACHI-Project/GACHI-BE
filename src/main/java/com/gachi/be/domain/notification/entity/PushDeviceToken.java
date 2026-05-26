package com.gachi.be.domain.notification.entity;

import com.gachi.be.domain.notification.entity.enums.PushPlatform;
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

/** 사용자의 React Native 앱 푸시 토큰을 저장하고 재등록을 흡수하는 엔티티. */
@Getter
@Entity
@Table(name = "push_device_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeviceToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PushPlatform platform;

  @Column(nullable = false, length = 512)
  private String token;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "device_id", length = 128)
  private String deviceId;

  @Column(name = "app_version", length = 50)
  private String appVersion;

  @Column(nullable = false)
  private boolean enabled;

  @Column(name = "last_registered_at", nullable = false)
  private OffsetDateTime lastRegisteredAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public PushDeviceToken(
      Long userId,
      PushPlatform platform,
      String token,
      String tokenHash,
      String deviceId,
      String appVersion) {
    this.userId = userId;
    this.platform = platform;
    this.token = token;
    this.tokenHash = tokenHash;
    this.deviceId = deviceId;
    this.appVersion = appVersion;
    this.enabled = true;
  }

  public void refresh(
      PushPlatform platform, String token, String tokenHash, String deviceId, String appVersion) {
    this.platform = platform;
    this.token = token;
    this.tokenHash = tokenHash;
    this.deviceId = deviceId;
    this.appVersion = appVersion;
    this.enabled = true;
    this.deletedAt = null;
    this.lastRegisteredAt = OffsetDateTime.now();
  }

  public void softDelete() {
    this.enabled = false;
    this.deletedAt = OffsetDateTime.now();
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (lastRegisteredAt == null) {
      lastRegisteredAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
