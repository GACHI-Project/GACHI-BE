package com.gachi.be.domain.newsletter.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterPage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedReason;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPausedStage;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterSourceType;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsletterStatusResponseTest {

    @Test
    void failedStatusIsRetryableAndContainsFailureStage() {
        Newsletter newsletter =
            Newsletter.builder()
                .userId(1L)
                .fileKey("newsletters/sample.png")
                .fileHash("hash")
                .status(NewsletterStatus.PROCESSING)
                .language("KO")
                .build();
        newsletter.fail("AI_SERVER", "timeout");

        NewsletterStatusResponse response = NewsletterStatusResponse.of(newsletter);

        assertThat(response.status()).isEqualTo(NewsletterStatus.FAILED);
        assertThat(response.canRetry()).isTrue();
        assertThat(response.failureStage()).isEqualTo("AI_SERVER");
    }

    @Test
    void pausedStatusContainsPausedPageAndButtonFlags() {
        Newsletter newsletter =
            Newsletter.builder()
                .userId(1L)
                .fileKey("newsletters/page1.jpg")
                .fileHash("hash")
                .status(NewsletterStatus.PROCESSING)
                .language("VI")
                .build();

        NewsletterPage done = page(1);
        done.completeOcr("ocr", "원문", null);
        done.completeTranslation("dịch", null);
        NewsletterPage unreadable = page(2);
        unreadable.markUnreadable();
        NewsletterPage pending = page(3);

        newsletter.initPageInfo(NewsletterSourceType.IMAGE, 3);
        newsletter.pause(
            2,
            NewsletterPausedStage.OCR,
            NewsletterPausedReason.UNREADABLE,
            OffsetDateTime.parse("2026-10-03T10:00:00+09:00"),
            null,
            null);

        NewsletterStatusResponse response =
            NewsletterStatusResponse.of(newsletter, List.of(done, unreadable, pending));

        assertThat(response.status()).isEqualTo(NewsletterStatus.PAUSED);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.processedPages()).isEqualTo(1);
        assertThat(response.pausedPageNo()).isEqualTo(2);
        assertThat(response.pausedStage()).isEqualTo(NewsletterPausedStage.OCR);
        assertThat(response.pausedReason()).isEqualTo(NewsletterPausedReason.UNREADABLE);
        assertThat(response.retryCount()).isZero();
        assertThat(response.retryable()).isTrue();
        assertThat(response.skippable()).isFalse();
        assertThat(response.canRetry()).isFalse();

        // 다시 시도 후에도 인식 불가 → 건너뛰기만
        unreadable.increaseRetryCount();
        unreadable.markUnreadable();
        NewsletterStatusResponse afterRetry =
            NewsletterStatusResponse.of(newsletter, List.of(done, unreadable, pending));
        assertThat(afterRetry.retryable()).isFalse();
        assertThat(afterRetry.skippable()).isTrue();
    }

    private NewsletterPage page(int pageNo) {
        return NewsletterPage.builder()
            .newsletterId(1L)
            .pageNo(pageNo)
            .fileKey("newsletters/page" + pageNo + ".jpg")
            .status(NewsletterPageStatus.PENDING)
            .build();
    }
}
