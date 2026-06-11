package com.gachi.be.domain.checklist.entity;

import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 체크리스트/해야할일 통합 엔티티 */
@Getter
@Entity
@Table(name = "checklist")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checklist {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "newsletter_id", nullable = false)
  private Long newsletterId;

  @Column(name = "calendar_event_id")
  private Long calendarEventId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private ChecklistType type;

  @Column(nullable = false, length = 500)
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "content_i18n", columnDefinition = "jsonb")
  private Map<String, String> contentI18n = new LinkedHashMap<>();

  @Column(length = 500)
  private String detail;

  @Column(name = "is_completed", nullable = false)
  private boolean completed = false;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "target_date_label", length = 50)
  private String targetDateLabel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public Checklist(
      Long newsletterId,
      Long calendarEventId,
      Long userId,
      ChecklistType type,
      String content,
      Map<String, String> contentI18n,
      String detail,
      LocalDate targetDate,
      String targetDateLabel) {
    this.newsletterId = newsletterId;
    this.userId = userId;
    this.type = type;
    this.content = content;
    this.contentI18n =
        contentI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(contentI18n);
    this.detail = detail;
    this.targetDate = targetDate;
    this.targetDateLabel = targetDateLabel;
    this.completed = false; // 생성 시 항상 미완료 상태
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    if (contentI18n == null) contentI18n = new LinkedHashMap<>();
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }

  /** 완료 상태 업데이트 */
  public void updateCompleted(boolean isCompleted) {
    this.completed = isCompleted;
  }

  /** 캘린더 일정 연결 시 사용 */
  public void linkToCalendarEvent(Long calendarEventId) {
    this.calendarEventId = calendarEventId;
  }
}
