package com.gachi.be.domain.schoolguide.entity;

import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
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

@Getter
@Entity
@Table(name = "school_guide")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolGuide {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private SchoolGuideCategory category;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String question;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String answer;

  @Column(name = "weekly_view_count", nullable = false)
  private long weeklyViewCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public SchoolGuide(SchoolGuideCategory category, String question, String answer) {
    this.category = category;
    this.question = question;
    this.answer = answer;
    this.weeklyViewCount = 0;
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

  /** 조회 시 weekly_view_count 증가 */
  public void incrementWeeklyViewCount() {
    this.weeklyViewCount++;
  }

  /** 질문 수정 */
  public void updateQuestion(String question) {
    this.question = question;
  }

  /** 답변 수정 */
  public void updateAnswer(String answer) {
    this.answer = answer;
  }

  /** 카테고리 수정 */
  public void updateCategory(SchoolGuideCategory category) {
    this.category = category;
  }

  /** 매주 월요일 00:00 주간 조회수 초기화 */
  public void resetWeeklyViewCount() {
    this.weeklyViewCount = 0;
  }
}
