package com.gachi.be.domain.checklist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 체크리스트 항목 엔티티.
 * - content: "현장학습 동의서 제출"  (굵게 표시되는 주요 항목)
 * - detail:  "담임 선생님께 원본 직접 제출" (한 줄 상세 설명)
 * checked: 사용자가 완료 체크 시 true로 업데이트. 기본값 false.
 */
@Getter
@Entity
@Table(name = "checklist_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "newsletter_id", nullable = false)
    private Long newsletterId;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "is_checked", nullable = false)
    private boolean checked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    public ChecklistItem(Long newsletterId, String content, String detail) {
        this.newsletterId = newsletterId;
        this.content = content;
        this.detail = detail;
        this.checked = false;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public void check() {
        this.checked = true;
    }

    public void uncheck() {
        this.checked = false;
    }
}
