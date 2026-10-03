package com.gachi.be.domain.newsletter.entity;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
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

/**
 * 가정통신문 페이지 1장의 OCR/번역 결과를 관리하는 엔티티.
 *
 * 문서 전체 결과(newsletter.original_text / translated_text)는 이 테이블의 페이지 결과를 순서대로 이어 붙여 만든다. AI 분석,
 * 챗봇, 중복 검사는 지금처럼 문서 전체 기준으로 동작한다.
 */
@Getter
@Entity
@Table(name = "newsletter_page")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsletterPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "newsletter_id", nullable = false)
    private Long newsletterId;

    /** 1부터 시작하는 페이지 번호. */
    @Column(name = "page_no", nullable = false)
    private Integer pageNo;

    /** 이미지는 해당 장의 원본 키, PDF는 모든 페이지가 같은 PDF 키. */
    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    /** 이미지 표시/OCR/AI 첨부용 키. EXIF 회전이 있으면 보정 JPEG 키, 없으면 원본 키. PDF는 null. */
    @Column(name = "display_file_key", length = 500)
    private String displayFileKey;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NewsletterPageStatus status;

    /** 현재 실패 사유로 사용자가 '다시 시도'를 누른 횟수. 실패 사유가 바뀌거나 성공하면 0으로 초기화된다. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "translated_text", columnDefinition = "TEXT")
    private String translatedText;

    /** 이미지 오버레이 블록 목록. PDF는 null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocks", columnDefinition = "jsonb")
    private List<NewsletterPageBlock> blocks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public NewsletterPage(
        Long newsletterId, Integer pageNo, String fileKey, NewsletterPageStatus status) {
        this.newsletterId = newsletterId;
        this.pageNo = pageNo;
        this.fileKey = fileKey;
        this.status = status != null ? status : NewsletterPageStatus.PENDING;
        this.retryCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = NewsletterPageStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** 이미지 표시용 파일 정보를 저장한다. (EXIF 보정 결과) */
    public void updateDisplayImage(String displayFileKey, int imageWidth, int imageHeight) {
        this.displayFileKey = displayFileKey;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    /** OCR 성공. 번역 대기 상태(OCR_DONE)로 바꾸고 이전 실패 횟수를 초기화한다. */
    public void completeOcr(String ocrText, String originalText, List<NewsletterPageBlock> blocks) {
        this.ocrText = ocrText;
        this.originalText = originalText;
        this.translatedText = null;
        this.blocks = blocks == null ? null : new ArrayList<>(blocks);
        this.status = NewsletterPageStatus.OCR_DONE;
        this.retryCount = 0;
    }

    /** 번역 성공(또는 한국어 사용자라 번역 생략). 페이지 처리를 끝낸다. */
    public void completeTranslation(String translatedText, List<NewsletterPageBlock> blocks) {
        this.translatedText = translatedText;
        if (blocks != null) {
            this.blocks = new ArrayList<>(blocks);
        }
        this.status = NewsletterPageStatus.SUCCESS;
        this.retryCount = 0;
    }

    /** OCR 통신 실패. */
    public void markOcrFailed() {
        changeFailureStatus(NewsletterPageStatus.OCR_FAILED);
    }

    /** 글자 인식 불가. */
    public void markUnreadable() {
        changeFailureStatus(NewsletterPageStatus.UNREADABLE);
    }

    /** 번역 실패. */
    public void markTranslationFailed() {
        changeFailureStatus(NewsletterPageStatus.TRANSLATION_FAILED);
    }

    /** 건너뛰기. 원문이 있으면 번역만 건너뛴 페이지, 없으면 글자를 읽지 못한 페이지가 된다. */
    public void skip() {
        this.translatedText = null;
        this.status = NewsletterPageStatus.SKIPPED;
        this.retryCount = 0;
    }

    /** 사용자가 '다시 시도'를 누른 횟수를 1 올린다. 실제 재처리는 파이프라인이 실패 상태를 보고 다시 수행한다. */
    public void increaseRetryCount() {
        this.retryCount++;
    }

    /**
     * 문서 전체 다시 분석(/analysis/retry) 준비. 결정 사항: OCR 결과는 재사용하고 번역은 처음부터 다시 한다. (언어 변경으로 FAILED 된 문서도 언어가
     * 섞이지 않게 하기 위함)
     *
     * 원문이 있는 페이지는 OCR_DONE으로 되돌려 번역만 다시 한다. 원문이 없는 페이지는 이미지라면 OCR부터 다시 시도하고(PENDING), PDF라면 해당
     * 페이지만 다시 OCR할 수 없으므로 건너뛴 상태(SKIPPED)를 유지한다.
     *
     * @param canRetryOcr 페이지 단위로 OCR을 다시 할 수 있는지 (이미지 true, PDF false)
     */
    public void resetForReanalysis(boolean canRetryOcr) {
        this.retryCount = 0;
        this.translatedText = null;
        if (this.blocks != null) {
            this.blocks = this.blocks.stream().map(block -> block.withTranslatedText(null)).toList();
        }
        boolean hasOriginalText = this.originalText != null && !this.originalText.isBlank();
        if (hasOriginalText) {
            this.status = NewsletterPageStatus.OCR_DONE;
        } else {
            this.status = canRetryOcr ? NewsletterPageStatus.PENDING : NewsletterPageStatus.SKIPPED;
        }
    }

    /** 현재 상태가 멈춤 사유(실패 상태)라면 그 사유를 반환한다. */
    public NewsletterPausedReason resolvePausedReason() {
        return NewsletterPausedReason.fromPageStatus(this.status);
    }

    /** 현재 실패 상태에서 [다시 시도]가 가능한지. 실패 상태가 아니면 false. */
    public boolean isRetryable() {
        NewsletterPausedReason reason = resolvePausedReason();
        return reason != null && reason.isRetryable(this.retryCount);
    }

    /** 현재 실패 상태에서 [건너뛰기]가 가능한지. 실패 상태가 아니면 false. */
    public boolean isSkippable() {
        NewsletterPausedReason reason = resolvePausedReason();
        return reason != null && reason.isSkippable(this.retryCount);
    }

    /** 실패 사유가 바뀌면 다시 시도 횟수를 0부터 다시 센다. (같은 사유로 다시 실패하면 횟수 유지) */
    private void changeFailureStatus(NewsletterPageStatus failureStatus) {
        if (this.status != failureStatus) {
            this.retryCount = 0;
        }
        this.status = failureStatus;
    }
}
