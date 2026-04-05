package com.gachi.be.domain.user.entity;

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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 인증/식별 정보를 포함한 사용자 루트 엔티티. */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(name = "login_id", nullable = false, unique = true, length = 50)
  private String loginId;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "phone_number", nullable = false, unique = true, length = 20)
  private String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Column(name = "email_verified_at")
  private OffsetDateTime emailVerifiedAt;

  @Column(name = "consent_agreed_at", nullable = false)
  private OffsetDateTime consentAgreedAt;

  @Column(name = "consent_version", nullable = false, length = 20)
  private String consentVersion;

  @Column(name = "password_updated_at", nullable = false)
  private OffsetDateTime passwordUpdatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Builder
  public User(
      String email,
      String loginId,
      String passwordHash,
      String name,
      String phoneNumber,
      UserStatus status,
      OffsetDateTime emailVerifiedAt,
      OffsetDateTime consentAgreedAt,
      String consentVersion,
      OffsetDateTime passwordUpdatedAt) {
    this.email = email;
    this.loginId = loginId;
    this.passwordHash = passwordHash;
    this.name = name;
    this.phoneNumber = phoneNumber;
    this.status = status;
    this.emailVerifiedAt = emailVerifiedAt;
    this.consentAgreedAt = consentAgreedAt;
    this.consentVersion = consentVersion;
    this.passwordUpdatedAt = passwordUpdatedAt;
  }

  /** 로그인 가능 상태인지 확인한다. */
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
    if (status == null) {
      status = UserStatus.ACTIVE;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
