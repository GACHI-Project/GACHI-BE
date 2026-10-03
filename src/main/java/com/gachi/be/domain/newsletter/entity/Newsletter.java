package com.gachi.be.domain.newsletter.entity;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedStage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
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

  /**
   * 여러 장 업로드 시 페이지 순서를 유지한 전체 S3 키 목록 file_key는 대표(첫 장) 키로 계속 유지하므로 기존 유니크 인덱스와 조회 코드는 그대로 동작. 단일
   * 업로드 시절에 저장된 레코드는 이 값이 NULL이므로, 실제 사용은 항상 resolveFileKeys()를 통해
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "file_keys", columnDefinition = "jsonb")
  private List<String> fileKeys = new ArrayList<>();

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

  /** 원본 파일 종류(PDF/IMAGE). 기능 도입 전 문서는 null이며 resolveSourceType()으로 확장자 기준 판단. */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", length = 10)
  private NewsletterSourceType sourceType;

  /** 전체 페이지 수. 이미지는 업로드 장 수, PDF는 클로바 OCR 응답 후 확정된다. */
  @Column(name = "total_pages")
  private Integer totalPages;

  /** PAUSED 상태일 때 멈춘 페이지 번호(1부터). */
  @Column(name = "paused_page_no")
  private Integer pausedPageNo;

  /** PAUSED 상태일 때 멈춘 단계(OCR/TRANSLATION). */
  @Enumerated(EnumType.STRING)
  @Column(name = "paused_stage", length = 20)
  private NewsletterPausedStage pausedStage;

  /** PAUSED 상태일 때 멈춘 사유(OCR_FAILED/UNREADABLE/TRANSLATION_FAILED). */
  @Enumerated(EnumType.STRING)
  @Column(name = "paused_reason", length = 30)
  private NewsletterPausedReason pausedReason;

  /** PAUSED로 전환된 시각. 24시간이 지나면 스케줄러가 FAILED로 전환한다. */
  @Column(name = "paused_at")
  private OffsetDateTime pausedAt;

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
      List<String> fileKeys,
      String fileHash,
      NewsletterStatus status,
      String language) {
    this.userId = userId;
    this.childName = childName;
    this.childGrade = childGrade;
    this.childColor = childColor;
    this.fileKey = fileKey;
    this.fileKeys = fileKeys == null ? new ArrayList<>() : new ArrayList<>(fileKeys);
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
    if (fileKeys == null) fileKeys = new ArrayList<>();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = OffsetDateTime.now();
    if (dateCandidates == null) dateCandidates = new ArrayList<>();
    if (titleI18n == null) titleI18n = new LinkedHashMap<>();
    if (fileKeys == null) fileKeys = new ArrayList<>();
  }

  /**
   * OCR 대상 파일 키 목록을 페이지 순서대로 반환. file_keys 컬럼이 없던 시절에 저장된 레코드(NULL 또는 빈 배열)는 file_key 단건으로 대체
   * 반환하므로, 파이프라인은 데이터 마이그레이션 없이 과거 문서도 그대로 재분석 가능.
   */
  public List<String> resolveFileKeys() {
    if (fileKeys == null || fileKeys.isEmpty()) {
      return fileKey == null ? List.of() : List.of(fileKey);
    }
    return List.copyOf(fileKeys);
  }

  /** 원본 파일 종류를 반환한다. source_type이 없던 과거 문서는 대표 파일 키의 확장자로 판단한다. */
  public NewsletterSourceType resolveSourceType() {
      if (sourceType != null) {
          return sourceType;
      }
      return NewsletterSourceType.fromFileKey(fileKey);
  }

  /** 페이지 처리 시작 시 원본 종류와 전체 페이지 수를 기록한다. (PDF는 OCR 응답 후 다시 호출해 페이지 수를 확정) */
  public void initPageInfo(NewsletterSourceType sourceType, Integer totalPages) {
      this.sourceType = sourceType;
      this.totalPages = totalPages;
  }

  /**
   * 특정 페이지 실패로 사용자 선택을 기다리는 PAUSED 상태로 전환한다. 24시간 방치 후 FAILED로 바뀌어도 원문을 볼 수 있도록 지금까지 만든 원문 스냅샷을 함께
   * 저장한다. (OCR 단계에서 멈추면 아직 원문이 없으므로 null이 들어온다.)
   */
  public void pause(
      int pausedPageNo,
      NewsletterPausedStage pausedStage,
      NewsletterPausedReason pausedReason,
      OffsetDateTime pausedAt,
      String ocrText,
      String originalText) {
      this.status = NewsletterStatus.PAUSED;
      this.pausedPageNo = pausedPageNo;
      this.pausedStage = pausedStage;
      this.pausedReason = pausedReason;
      this.pausedAt = pausedAt;
      if (ocrText != null) {
          this.ocrText = ocrText;
      }
      if (originalText != null) {
          this.originalText = originalText;
      }
  }

  /** 멈춤 정보를 비운다. (이어서 진행/건너뛰기/완료/실패 시) */
  public void clearPause() {
      this.pausedPageNo = null;
      this.pausedStage = null;
      this.pausedReason = null;
      this.pausedAt = null;
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
