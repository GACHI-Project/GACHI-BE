package com.gachi.be.domain.checklist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [type = "CHECKLIST"] 가정통신문에서 추출한 준비/제출 사항
 *   - content  : "현장학습 동의서 제출" (굵게 표시되는 주요 항목)
 *   - detail   : "담임 선생님께 원본 직접 제출" (한 줄 상세 설명, null 가능)
 *   - targetDate / targetDateLabel : 사용 안 함 (null)
 *
 * [type = "TODO"] 날짜 기반 행동 계획
 *   - content          : "동의서에 서명 후 가방에 넣기"
 *   - targetDate       : 2026-05-15 (null 이면 즉시 행동)
 *   - targetDateLabel  : "5월 15일" / "지금 바로" 등 사용자 표시 문구
 *   - detail           : 사용 안 함 (null)
 */
@Getter
@Entity
@Table(name = "checklist")  // V7 migration에서 생성한 테이블
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 항목이 속한 가정통신문 ID. newsletter 삭제 시 CASCADE로 함께 삭제됨. */
    @Column(name = "newsletter_id", nullable = false)
    private Long newsletterId;

    /**
     * 소유 사용자 ID.
     * newsletter.user_id 와 동일한 값이지만,
     * 완료 처리/삭제 시 소유권 검증을 위해 직접 저장 (JOIN 없이 바로 검증 가능).
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 항목 타입.
     * "CHECKLIST" : 준비/제출 사항
     * "TODO"      : 날짜 기반 행동 계획
     * DB에 CHECK 제약조건으로 이 두 값만 허용됨.
     */
    @Column(nullable = false, length = 10)
    private String type;

    /** 항목명 / 행동 내용. CHECKLIST/TODO 공통으로 사용. */
    @Column(nullable = false, length = 500)
    private String content;

    /**
     * 상세 설명.
     * CHECKLIST 타입에서만 의미 있음 (ex: "담임 선생님께 원본 직접 제출").
     * TODO 타입은 null.
     */
    @Column(length = 500)
    private String detail;

    /**
     * 완료 여부. 기본값 false.
     * PATCH /checklists/{id}/complete API로 true/false 토글.
     * CHECKLIST/TODO 모두 공통으로 사용.
     */
    @Column(name = "is_completed", nullable = false)
    private boolean completed = false;

    /**
     * 행동 목표 날짜 (TODO 타입 전용).
     * null 이면 즉시 행동을 의미 (targetDateLabel = "지금 바로").
     * CHECKLIST 타입은 null.
     */
    @Column(name = "target_date")
    private LocalDate targetDate;

    /**
     * 사용자에게 표시할 날짜 문구 (TODO 타입 전용).
     * ex: "지금 바로", "내일", "5월 21일"
     * CHECKLIST 타입은 null.
     */
    @Column(name = "target_date_label", length = 50)
    private String targetDateLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 빌더. CHECKLIST/TODO 모두 이 빌더 하나로 생성.
     * type 값에 따라 사용하지 않는 컬럼은 null로 넘기면 됨.
     *
     * CHECKLIST 생성 예:
     *   Checklist.builder()
     *     .newsletterId(1L).userId(1L).type("CHECKLIST")
     *     .content("동의서 제출").detail("담임 선생님께 직접")
     *     .targetDate(null).targetDateLabel(null).build();
     *
     * TODO 생성 예:
     *   Checklist.builder()
     *     .newsletterId(1L).userId(1L).type("TODO")
     *     .content("동의서에 서명 후 가방에 넣기").detail(null)
     *     .targetDate(LocalDate.of(2026, 5, 15)).targetDateLabel("5월 15일").build();
     */
    @Builder
    public Checklist(
        Long newsletterId,
        Long userId,
        String type,
        String content,
        String detail,
        LocalDate targetDate,
        String targetDateLabel) {
        this.newsletterId = newsletterId;
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.detail = detail;
        this.targetDate = targetDate;
        this.targetDateLabel = targetDateLabel;
        this.completed = false; // 생성 시 항상 미완료 상태
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

    /**
     * 완료 상태 업데이트.
     * PATCH /checklists/{id}/complete API에서 호출.
     * CHECKLIST/TODO 모두 이 메서드 하나로 처리.
     *
     * @param isCompleted true = 완료, false = 미완료
     */
    public void updateCompleted(boolean isCompleted) {
        this.completed = isCompleted;
    }
}
