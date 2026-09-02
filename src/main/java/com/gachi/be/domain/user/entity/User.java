package com.gachi.be.domain.user.entity;

import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
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

  @Column(name = "login_id", unique = true, length = 50)
  private String loginId;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "phone_number", unique = true, length = 20)
  private String phoneNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "language_code", nullable = false, length = 10)
  private String languageCode;

  @Column(name = "notification_enabled", nullable = false)
  private boolean notificationEnabled;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_preference", nullable = false, length = 20)
  private NotificationPreference notificationPreference;

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

  @Column(name = "password_change_required", nullable = false)
  private boolean passwordChangeRequired;

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
      String languageCode,
      Boolean notificationEnabled,
      NotificationPreference notificationPreference,
      OffsetDateTime emailVerifiedAt,
      OffsetDateTime consentAgreedAt,
      String consentVersion,
      OffsetDateTime passwordUpdatedAt,
      boolean passwordChangeRequired) {
    this.email = email;
    this.loginId = loginId;
    this.passwordHash = passwordHash;
    this.name = name;
    this.phoneNumber = phoneNumber;
    this.status = status;
    this.languageCode = languageCode != null ? languageCode : "KO";
    this.notificationPreference =
        notificationPreference != null
            ? notificationPreference
            : NotificationPreference.fromLegacyEnabled(notificationEnabled);
    this.notificationEnabled = this.notificationPreference.isPushEnabled();
    this.emailVerifiedAt = emailVerifiedAt;
    this.consentAgreedAt = consentAgreedAt;
    this.consentVersion = consentVersion;
    this.passwordUpdatedAt = passwordUpdatedAt;
    this.passwordChangeRequired = passwordChangeRequired;
  }

  /** 로그인 가능 상태인지 확인한다. */
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public void withdraw(OffsetDateTime withdrawnAt) {
    if (withdrawnAt == null) {
      throw new IllegalArgumentException("withdrawnAt은 비어 있을 수 없습니다.");
    }
    this.status = UserStatus.WITHDRAWN;
    this.deletedAt = withdrawnAt;
  }

  public void resetPassword(String passwordHash, OffsetDateTime passwordUpdatedAt) {
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash는 비어 있을 수 없습니다.");
    }
    if (passwordUpdatedAt == null) {
      throw new IllegalArgumentException("passwordUpdatedAt은 비어 있을 수 없습니다.");
    }
    this.passwordHash = passwordHash;
    this.passwordUpdatedAt = passwordUpdatedAt;
    this.passwordChangeRequired = false;
  }

  public void updateProfile(String name, String phoneNumber) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name은 비어 있을 수 없습니다.");
    }
    if (phoneNumber == null || phoneNumber.isBlank()) {
      throw new IllegalArgumentException("phoneNumber는 비어 있을 수 없습니다.");
    }
    this.name = name;
    this.phoneNumber = phoneNumber;
  }

  public void changeEmail(String email, OffsetDateTime emailVerifiedAt) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email은 비어 있을 수 없습니다.");
    }
    if (emailVerifiedAt == null) {
      throw new IllegalArgumentException("emailVerifiedAt은 비어 있을 수 없습니다.");
    }
    this.email = email;
    this.emailVerifiedAt = emailVerifiedAt;
  }

  public void updateLanguage(String languageCode) {
    if (languageCode == null || languageCode.isBlank()) {
      throw new IllegalArgumentException("languageCode는 비어 있을 수 없습니다.");
    }
    this.languageCode = languageCode.trim().toUpperCase();
  }

  public void updateNotificationEnabled(boolean notificationEnabled) {
    updateNotificationPreference(NotificationPreference.fromLegacyEnabled(notificationEnabled));
  }

  public void updateNotificationPreference(NotificationPreference notificationPreference) {
    this.notificationPreference =
        notificationPreference != null
            ? notificationPreference
            : NotificationPreference.defaultValue();
    this.notificationEnabled = this.notificationPreference.isPushEnabled();
  }

  public void withdraw(OffsetDateTime withdrawnAt) {
    if (withdrawnAt == null) {
      throw new IllegalArgumentException("withdrawnAt은 비어 있을 수 없습니다.");
    }
    this.status = UserStatus.WITHDRAWN;
    this.deletedAt = withdrawnAt;
  }

  public NotificationPreference getNotificationPreference() {
    return notificationPreference != null
        ? notificationPreference
        : NotificationPreference.fromLegacyEnabled(notificationEnabled);
  }

  public boolean isNotificationEnabled() {
    return getNotificationPreference().isPushEnabled();
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
    if (languageCode == null) {
      languageCode = "KO";
    }
    if (notificationPreference == null) {
      notificationPreference = NotificationPreference.fromLegacyEnabled(notificationEnabled);
    }
    notificationEnabled = notificationPreference.isPushEnabled();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
