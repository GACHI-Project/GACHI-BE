package com.gachi.be.domain.todo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 해야 할 일 항목 엔티티. OpenAI가 가정통신문에서 날짜 기반으로 추출한 행동 계획. - content: "담임 선생님께 동의서 직접 제출" targetDate:
 * 2026-05-15 (null 도 가능하게 처리) / targetDateLabel: "5월 15일" targetDate: 실제 날짜가 명시된 경우. 캘린더 연동에 사용.
 * targetDateLabel: 사용자에게 보여줄 표시 문구.
 */
@Getter
@Entity
@Table(name = "todo_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TodoItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "newsletter_id", nullable = false)
  private Long newsletterId;

  @Column(name = "content", nullable = false, length = 500)
  private String content;

  @Column(name = "target_date")
  private LocalDate targetDate;

  @Column(name = "target_date_label", length = 50)
  private String targetDateLabel;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Builder
  public TodoItem(Long newsletterId, String content, LocalDate targetDate, String targetDateLabel) {
    this.newsletterId = newsletterId;
    this.content = content;
    this.targetDate = targetDate;
    this.targetDateLabel = targetDateLabel;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = OffsetDateTime.now();
  }
}
