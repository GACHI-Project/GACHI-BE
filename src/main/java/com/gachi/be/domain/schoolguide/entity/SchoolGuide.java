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
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "question_i18n", columnDefinition = "jsonb")
  private Map<String, String> questionI18n = new LinkedHashMap<>();

  @Column(nullable = false, columnDefinition = "TEXT")
  private String answer;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "answer_i18n", columnDefinition = "jsonb")
  private Map<String, String> answerI18n = new LinkedHashMap<>();

  @Column(name = "weekly_view_count", nullable = false)
  private long weeklyViewCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public SchoolGuide(
      SchoolGuideCategory category,
      String question,
      Map<String, String> questionI18n,
      String answer,
      Map<String, String> answerI18n) {
    this.category = category;
    this.question = question;
    this.questionI18n =
        questionI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(questionI18n);
    this.answer = answer;
    this.answerI18n = answerI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(answerI18n);
    this.weeklyViewCount = 0;
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    if (questionI18n == null) questionI18n = new LinkedHashMap<>();
    if (answerI18n == null) answerI18n = new LinkedHashMap<>();
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
  public void updateQuestion(String question, Map<String, String> questionI18n) {
    this.question = question;
    this.questionI18n =
        questionI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(questionI18n);
  }

  /** 답변 수정 */
  public void updateAnswer(String answer, Map<String, String> answerI18n) {
    this.answer = answer;
    this.answerI18n = answerI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(answerI18n);
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
