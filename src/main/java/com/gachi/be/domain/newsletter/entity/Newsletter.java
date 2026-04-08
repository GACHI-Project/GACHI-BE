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
 * 가정통신문 파일 자체와 AI 분석 상태를 관리하는 엔티티.
 *
 * <p>사용자가 파일을 업로드하는 순간 즉시 생성되며, S3 파일 경로·중복 방지 해시·분석
 * 진행상태 등의 메타 정보를 보관한다.
 *
 * <p>실제 AI 분석 결과(OCR 원문, 번역문, 요약 등)는 newsletter_analysis 테이블에
 * 별도 저장된다.
 */
@Getter
@Entity
@Table(name = "newsletter")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Newsletter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 가정통신문을 업로드한 사용자 ID (users.id 참조). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 연결된 자녀 ID (children.id 참조).
     *
     * <p>사용자가 자녀를 선택하지 않거나 "어느 아이인지 모르겠어요"를 선택한 경우 NULL.
     */
    @Column(name = "child_id")
    private Long childId;

    /**
     * "어느 아이인지 모르겠어요" 옵션 선택 여부.
     *
     * <p>TRUE이면 child_id는 NULL이고, 이 가정통신문은 특정 자녀에게 귀속되지 않는다.
     */
    @Column(name = "child_unknown", nullable = false)
    private boolean childUnknown = false;

    /**
     * AWS S3에 저장된 파일의 오브젝트 키(경로).
     *
     * <p>예: "newsletters/2026/04/08/uuid-파일명.jpg"
     * Presigned URL 생성 시 이 값을 사용한다.
     */
    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    /**
     * 파일 내용의 SHA-256 해시값.
     *
     * <p>동일한 파일의 중복 업로드를 방지하기 위해 사용한다.
     * Partial Unique Index(child_id IS NOT NULL이면 child_id+file_hash,
     * NULL이면 user_id+file_hash)로 DB에서 중복을 제어한다.
     */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    /**
     * AI 분석 파이프라인의 현재 진행 상태.
     *
     * <p>PENDING → PROCESSING → COMPLETED / FAILED 순으로 전이된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsletterStatus status;

    /**
     * FAILED 상태일 때 실패 원인을 기록하는 필드.
     *
     * <p>AI 서버나 외부 API 오류 메시지를 저장해두어 운영 디버깅에 활용한다.
     */
    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    /**
     * "저장하기" 버튼 클릭 여부.
     *
     * <p>FALSE인 동안에는 홈 화면과 문서 목록에 노출되지 않는다.
     * 분석 결과 화면에서 사용자가 "저장하기"를 누르면 TRUE로 업데이트된다.
     */
    @Column(name = "is_saved", nullable = false)
    private boolean saved = false;

    /** AI 파이프라인 버전 (운영 추적용). */
    @Column(name = "pipeline_version", length = 50)
    private String pipelineVersion;

    /** 분석 실패 후 재시도 횟수. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** AI 서버가 분석을 시작한 시각. */
    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    /** AI 분석이 완료된 시각 (COMPLETED 상태 전이 시각). */
    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    /** AI 분석이 실패한 시각 (FAILED 상태 전이 시각). */
    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 가정통신문 엔티티 생성용 빌더.
     *
     * <p>파일 업로드 시점에 status=PENDING으로 생성된다.
     */
    @Builder
    public Newsletter(
        Long userId,
        Long childId,
        boolean childUnknown,
        String fileKey,
        String fileHash,
        NewsletterStatus status) {
        this.userId = userId;
        this.childId = childId;
        this.childUnknown = childUnknown;
        this.fileKey = fileKey;
        this.fileHash = fileHash;
        this.status = status;
    }

    /** 엔티티 최초 저장 시 생성/수정 일시를 현재 시각으로 초기화한다. */
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = NewsletterStatus.PENDING;
        }
    }

    /** 엔티티 수정 시 updated_at을 갱신한다. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── 상태 전이 메서드들 ──────────────────────────────────────────

    /**
     * AI 분석을 시작할 때 호출한다.
     *
     * <p>status를 PROCESSING으로 변경하고 시작 시각을 기록한다.
     */
    public void startProcessing() {
        this.status = NewsletterStatus.PROCESSING;
        this.processingStartedAt = OffsetDateTime.now();
    }

    /**
     * AI 분석이 완료되었을 때 호출한다.
     *
     * <p>status를 COMPLETED로 변경하고 완료 시각을 기록한다.
     */
    public void complete() {
        this.status = NewsletterStatus.COMPLETED;
        this.processedAt = OffsetDateTime.now();
    }

    /**
     * AI 분석이 실패했을 때 호출한다.
     *
     * <p>status를 FAILED로 변경하고 실패 사유와 시각을 기록한다.
     *
     * @param reason 실패 원인 메시지 (error_log에 저장됨)
     */
    public void fail(String reason) {
        this.status = NewsletterStatus.FAILED;
        this.errorLog = reason;
        this.failedAt = OffsetDateTime.now();
    }

    /**
     * 사용자가 "저장하기" 버튼을 클릭했을 때 호출한다.
     *
     * <p>is_saved를 TRUE로 변경하여 홈 화면·문서 목록에 노출되게 한다.
     */
    public void markAsSaved() {
        this.saved = true;
    }
}
