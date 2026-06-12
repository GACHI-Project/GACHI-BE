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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  @Column(name = "content_hash", length = 64)
  private String contentHash;

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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "title_i18n", columnDefinition = "jsonb")
  private Map<String, String> titleI18n;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "failure_stage", length = 50)
  private String failureStage;

  @Column(name = "failure_reason", columnDefinition = "TEXT")
  private String failureReason;

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
    if (titleI18n == null) titleI18n = new LinkedHashMap<>();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
    if (dateCandidates == null) dateCandidates = new ArrayList<>();
    if (titleI18n == null) titleI18n = new LinkedHashMap<>();
  }

  /** AI 분석 시작 시 PROCESSING 상태로 전환합니다. */
  public void startProcessing() {
    this.status = NewsletterStatus.PROCESSING;
    this.failureStage = null;
    this.failureReason = null;
  }

  /** AI 분석 결과를 저장하고 COMPLETED 상태로 전환합니다. */
  public void complete(
      String ocrText, String originalText, String translatedText, String title, String summary) {
    complete(ocrText, originalText, translatedText, title, null, summary);
  }

  /** AI 분석 결과와 알림 렌더링용 다국어 제목을 저장하고 COMPLETED 상태로 전환합니다. */
  public void complete(
      String ocrText,
      String originalText,
      String translatedText,
      String title,
      Map<String, String> titleI18n,
      String summary) {
    this.ocrText = ocrText;
    this.originalText = originalText;
    this.translatedText = translatedText;
    this.title = title;
    this.titleI18n = titleI18n == null ? new LinkedHashMap<>() : new LinkedHashMap<>(titleI18n);
    this.summary = summary;
    this.status = NewsletterStatus.COMPLETED;
    this.failureStage = null;
    this.failureReason = null;
  }

  /** 분석 실패 시 원인 추적을 위해 실패 단계와 사유를 함께 저장합니다. */
  public void fail(String failureStage, String failureReason) {
    this.status = NewsletterStatus.FAILED;
    this.failureStage = normalizeFailureStage(failureStage);
    this.failureReason = normalizeFailureReason(failureReason);
  }

  /** OCR/번역 이후 AI 서버 장애가 나도 사용자가 원문 결과를 확인할 수 있도록 중간 산출물을 보존합니다. */
  public void failWithSnapshot(
      String ocrText,
      String originalText,
      String translatedText,
      String failureStage,
      String failureReason) {
    this.ocrText = ocrText;
    this.originalText = originalText;
    this.translatedText = translatedText;
    fail(failureStage, failureReason);
  }

  /** OCR 결과 기반 본문 해시를 저장합니다. 재촬영처럼 파일 해시가 달라도 같은 문서인지 판단하기 위한 값입니다. */
  public void updateContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  /** 실패한 분석을 사용자가 다시 시도할 때 이전 실패 사유를 비우고 대기 상태로 되돌립니다. */
  public void prepareRetry() {
    this.status = NewsletterStatus.PENDING;
    this.failureStage = null;
    this.failureReason = null;
    this.title = null;
    this.titleI18n = new LinkedHashMap<>();
    this.summary = null;
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

  private String normalizeFailureStage(String failureStage) {
    if (failureStage == null || failureStage.isBlank()) {
      return "UNKNOWN";
    }
    return failureStage.length() <= 50 ? failureStage : failureStage.substring(0, 50);
  }

  private String normalizeFailureReason(String failureReason) {
    if (failureReason == null || failureReason.isBlank()) {
      return null;
    }
    return failureReason.length() <= 1000 ? failureReason : failureReason.substring(0, 1000);
  }
}
