package com.gachi.be.domain.auth.entity;

import com.gachi.be.domain.user.entity.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_social_accounts_provider_user",
          columnNames = {"provider", "provider_user_id"}),
      @UniqueConstraint(
          name = "uk_social_accounts_user_provider",
          columnNames = {"user_id", "provider"})
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SocialProvider provider;

  @Column(name = "provider_user_id", nullable = false, length = 100)
  private String providerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "connection_status", nullable = false, length = 30)
  private SocialAccountConnectionStatus connectionStatus;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public SocialAccount(User user, SocialProvider provider, String providerUserId) {
    this.user = user;
    this.provider = provider;
    this.providerUserId = providerUserId;
    this.connectionStatus = SocialAccountConnectionStatus.ACTIVE;
  }

  public void requestDisconnect() {
    this.connectionStatus = SocialAccountConnectionStatus.DISCONNECT_PENDING;
  }

  public boolean isDisconnectPending() {
    return connectionStatus == SocialAccountConnectionStatus.DISCONNECT_PENDING;
  }

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (connectionStatus == null) {
      connectionStatus = SocialAccountConnectionStatus.ACTIVE;
    }
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
