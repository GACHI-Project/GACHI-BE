package com.gachi.be.domain.auth.entity;

import com.gachi.be.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 기기/브라우저 단위 로그인 세션을 나타내는 리프레시 토큰 엔티티. */
@Getter
@Entity
@Table(name = "auth_refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthRefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, unique = true, length = 255)
  private String tokenHash;

  @Column(nullable = false, unique = true, length = 36)
  private String jti;

  @Column(name = "device_info", length = 255)
  private String deviceInfo;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "remember_me", nullable = false)
  private boolean rememberMe;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public AuthRefreshToken(
      User user,
      String tokenHash,
      String jti,
      String deviceInfo,
      String ipAddress,
      boolean rememberMe,
      OffsetDateTime expiresAt,
      OffsetDateTime lastUsedAt) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.jti = jti;
    this.deviceInfo = deviceInfo;
    this.ipAddress = ipAddress;
    this.rememberMe = rememberMe;
    this.expiresAt = expiresAt;
    this.lastUsedAt = lastUsedAt;
  }

  /** 재발급 회전을 위해 기존 리프레시 토큰을 철회한다. */
  public void revoke() {
    this.revokedAt = OffsetDateTime.now();
    this.lastUsedAt = this.revokedAt;
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
