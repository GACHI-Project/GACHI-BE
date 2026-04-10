package com.gachi.be.domain.newsletter.entity;

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
import com.gachi.be.domain.newsletter.entity.enums.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 가정통신문 파일 메타정보 + AI 분석 결과를 통합 관리
 */
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

    /**
     * 업로드 시점의 자녀 이름 -> 스냅샷 방식
     */
    @Column(name = "child_name", length = 50)
    private String childName;

    /**
     * 업로드 시점의 자녀 학년 -> 스냅샷 방식
     */
    @Column(name = "child_grade")
    private Integer childGrade;

    /**
     * 자녀 캘린더 색상 HEX 코드 (동기화 대상으로 처리).
     */
    @Column(name = "child_color", length = 7)
    private String childColor;

    /**
     * AWS S3에 저장된 파일의 오브젝트 키(경로).
     * Presigned URL 생성 시 + 클로바 OCR URL 전달에 사용 예정
     */
    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    /**
     * 파일 내용의 SHA-256 해시값.
     * 동일한 파일의 중복 업로드를 방지하기 위해 사용.
     * Partial Unique Index 사용
     */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /**
     * AI 분석 파이프라인의 현재 진행 상태.
     * PENDING → PROCESSING → COMPLETED / FAILED 순
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsletterStatus status;

    /**
     * "저장하기" 버튼 클릭 여부.
     * FALSE -> 홈 화면, 문서 목록 노출x, 분석 결과 화면에서 저장하기 누르면 TREU 업데이트
     */
    @Column(name = "is_saved", nullable = false)
    private boolean saved = false;

    /**
     * 클로바 OCR이 추출한 원문 텍스트.
     * fields 배열의 inferText를 Y좌표 기반으로 정렬·합친 결과.
     * AI 분석 완료 전까지 NULL.
     */
    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    /**
     * 전처리·정제된 최종 원문.
     * ocr_text에서 노이즈 제거·공백 정규화 후처리를 거친 결과.
     * 번역·요약의 실제 입력값으로 사용 예정.
     */
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    /**
     * 번역된 텍스트.
     * language=KO인 경우 NULL (번역 스킵). 그 외 파파고 API 번역 결과.
     */
    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    /**
     * AI가 추출한 가정통신문 제목.
     * KO: 원문 제목 / 그 외: 번역된 제목. 문서 목록 화면에 표시.
     */
    @Column(name = "title", length = 255)
    private String title;

    /**
     * AI 생성 요약문.
     * OpenAI API로 생성 예정.
     */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /**
     * users.language_code 변경되어도 이 문서의 분석 언어는 유지.
     */
    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 파일 업로드 시점에 newsletter 레코드를 생성하는 빌더.
     * AI 결과 컬럼들(ocrText 등)은 업로드 시점에 NULL이며
     * 비동기 AI 파이프라인에서 채울 예정.
     */
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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // 상태 전이 메서드

    /** AI 분석 시작. PROCESSING으로 전이. */
    public void startProcessing() {
        this.status = NewsletterStatus.PROCESSING;
    }

    /**
     * AI 분석 완료. 결과를 저장하고 COMPLETED로 전이.
     *
     * @param ocrText     OCR 추출 원문
     * @param originalText 정제된 원문
     * @param translatedText 번역문 (KO면 null)
     * @param title       추출된 제목
     * @param summary     AI 요약문
     */
    public void complete(
        String ocrText,
        String originalText,
        String translatedText,
        String title,
        String summary) {
        this.ocrText = ocrText;
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.title = title;
        this.summary = summary;
        this.status = NewsletterStatus.COMPLETED;
    }

    /** AI 분석 실패. FAILED로 전이. */
    public void fail() {
        this.status = NewsletterStatus.FAILED;
    }

    /** "저장하기" 클릭 처리. 홈 화면·문서 목록에 노출 O*/
    public void markAsSaved() {
        this.saved = true;
    }

    /**
     * 자녀 색상 동기화.
     * ChildService에서 색상 변경 시 이 메서드로 newsletter도 업데이트
     */
    public void updateChildColor(String newColor) {
        this.childColor = newColor;
    }
}
