package com.gachi.be.domain.calendar.entity;

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

/** 캘린더 일정 엔티티. */
@Getter
@Entity
@Table(name = "calendar_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "newsletter_id", nullable = false)
  private Long newsletterId;

  @Column(name = "child_name", length = 50)
  private String childName;

  @Column(name = "child_color", length = 7)
  private String childColor;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "external_key", length = 255, unique = true)
  private String externalKey;

  @Column(name = "start_at", nullable = false)
  private OffsetDateTime startAt;

  @Column(name = "end_at")
  private OffsetDateTime endAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public CalendarEvent(
      Long userId,
      Long newsletterId,
      String childName,
      String childColor,
      String title,
      String description,
      String externalKey,
      OffsetDateTime startAt,
      OffsetDateTime endAt) {
    this.userId = userId;
    this.newsletterId = newsletterId;
    this.childName = childName;
    this.childColor = childColor;
    this.title = title;
    this.description = description;
    this.externalKey = externalKey;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  public void updateChildColor(String newColor) {
    this.childColor = newColor;
  }
}
