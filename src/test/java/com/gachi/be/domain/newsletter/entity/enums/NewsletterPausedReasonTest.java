package com.gachi.be.domain.newsletter.entity.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// 멈춤 사유별 버튼 노출 규칙 테스트
class NewsletterPausedReasonTest {

    @Test
    void unreadableShowsRetryFirstAndThenOnlySkip() {
        assertThat(NewsletterPausedReason.UNREADABLE.isRetryable(0)).isTrue();
        assertThat(NewsletterPausedReason.UNREADABLE.isSkippable(0)).isFalse();

        assertThat(NewsletterPausedReason.UNREADABLE.isRetryable(1)).isFalse();
        assertThat(NewsletterPausedReason.UNREADABLE.isSkippable(1)).isTrue();
    }

    @Test
    void communicationFailureShowsRetryFirstAndThenRetryAndSkip() {
        for (NewsletterPausedReason reason :
            new NewsletterPausedReason[] {
                NewsletterPausedReason.OCR_FAILED, NewsletterPausedReason.TRANSLATION_FAILED
            }) {
            assertThat(reason.isRetryable(0)).isTrue();
            assertThat(reason.isSkippable(0)).isFalse();

            assertThat(reason.isRetryable(1)).isTrue();
            assertThat(reason.isSkippable(1)).isTrue();

            assertThat(reason.isRetryable(5)).isTrue();
        }
    }

    @Test
    void fromPageStatusMapsOnlyFailureStatuses() {
        assertThat(NewsletterPausedReason.fromPageStatus(NewsletterPageStatus.UNREADABLE))
            .isEqualTo(NewsletterPausedReason.UNREADABLE);
        assertThat(NewsletterPausedReason.fromPageStatus(NewsletterPageStatus.OCR_FAILED))
            .isEqualTo(NewsletterPausedReason.OCR_FAILED);
        assertThat(NewsletterPausedReason.fromPageStatus(NewsletterPageStatus.TRANSLATION_FAILED))
            .isEqualTo(NewsletterPausedReason.TRANSLATION_FAILED);
        assertThat(NewsletterPausedReason.fromPageStatus(NewsletterPageStatus.SUCCESS)).isNull();
        assertThat(NewsletterPausedReason.fromPageStatus(null)).isNull();
    }
}
