package com.gachi.be.domain.newsletter.entity;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 가정통신문 파일 메타데이터와 AI 분석 결과를 함께 관리하는 엔티티입니다. */
@Getter
@Entity
@Table(name = "newsletter")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Newsletter {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "child_name", length = 50)
  private String childName;

  @Column(name = "child_grade")
  private Integer childGrade;

  @Column(name = "child_color", length = 7)
  private String childColor;

  @Column(name = "file_key", nullable = false, length = 500)
  private String fileKey;

  @Column(name = "file_hash", nullable = false, length = 64)
  private String fileHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NewsletterStatus status;

  @Column(name = "is_saved", nullable = false)
  private boolean saved = true;

  @Column(name = "ocr_text", columnDefinition = "TEXT")
  private String ocrText;

  @Column(name = "original_text", columnDefinition = "TEXT")
  private String originalText;

  @Column(name = "translated_text", columnDefinition = "TEXT")
  private String translatedText;

  @Column(name = "title", length = 255)
  private String title;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  /** 날짜 후보는 최종 일정이 아니라 후속 AI 매칭을 위한 중간 재료이므로 JSON으로 보관합니다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "date_candidates", columnDefinition = "jsonb")
  private List<NewsletterDateCandidate> dateCandidates = new ArrayList<>();

  @Column(name = "language", nullable = false, length = 10)
  private String language;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Builder
  public Newsletter(
      Long userId,
      String childName,
      Integer childGrade,
      String childColor,
      String fileKey,
      String fileHash,
      NewsletterStatus status,
      String language) {
    this.userId = userId;
    this.childName = childName;
    this.childGrade = childGrade;
    this.childColor = childColor;
    this.fileKey = fileKey;
    this.fileHash = fileHash;
    this.status = status;
    this.language = language != null ? language : "KO";
  }

  @PrePersist
  protected void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
    if (status == null) status = NewsletterStatus.PENDING;
    if (language == null) language = "KO";
    if (dateCandidates == null) dateCandidates = new ArrayList<>();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
    if (dateCandidates == null) dateCandidates = new ArrayList<>();
  }

  /** AI 분석 시작 시 PROCESSING 상태로 전환합니다. */
  public void startProcessing() {
    this.status = NewsletterStatus.PROCESSING;
  }

  /** AI 분석 결과를 저장하고 COMPLETED 상태로 전환합니다. */
  public void complete(
      String ocrText, String originalText, String translatedText, String title, String summary) {
    this.ocrText = ocrText;
    this.originalText = originalText;
    this.translatedText = translatedText;
    this.title = title;
    this.summary = summary;
    this.status = NewsletterStatus.COMPLETED;
  }

  /** AI 분석 실패 시 FAILED 상태로 전환합니다. */
  public void fail() {
    this.status = NewsletterStatus.FAILED;
  }

  /** 날짜 후보 목록을 교체합니다. 후보가 없으면 빈 목록으로 저장합니다. */
  public void replaceDateCandidates(List<NewsletterDateCandidate> dateCandidates) {
    this.dateCandidates =
        dateCandidates == null ? new ArrayList<>() : new ArrayList<>(dateCandidates);
  }

  /** 자녀 색상 변경 시 가정통신문에 복사된 색상도 함께 갱신합니다. */
  public void updateChildColor(String newColor) {
    this.childColor = newColor;
  }
}
