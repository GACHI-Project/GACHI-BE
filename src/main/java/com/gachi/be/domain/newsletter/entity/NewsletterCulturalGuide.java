package com.gachi.be.domain.newsletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 가정통신문에서 AI가 선정한 문화 맥락 안내(학교 생활 가이드 FAQ) 매핑.
 *
 * 질문/답변 텍스트를 복사 저장하지 않고 school_guide_id만 보관한다. 조회 시 school_guide의 question_i18n /
 * answer_i18n을 사용자 언어로 해석해서 반환하므로 별도 번역이 필요 없다.
 */
@Getter
@Entity
@Table(name = "newsletter_cultural_guides")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsletterCulturalGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "newsletter_id", nullable = false)
    private Long newsletterId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "school_guide_id", nullable = false)
    private Long schoolGuideId;

    /** AI가 반환한 순서. 0부터 시작하며 화면 노출 순서로 사용된다. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public NewsletterCulturalGuide(
        Long newsletterId, Long userId, Long schoolGuideId, int displayOrder) {
        this.newsletterId = newsletterId;
        this.userId = userId;
        this.schoolGuideId = schoolGuideId;
        this.displayOrder = displayOrder;
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
}
