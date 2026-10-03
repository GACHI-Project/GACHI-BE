package com.gachi.be.domain.newsletter.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.NewsletterPageBlock.BlockBox;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterPageStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

// 페이지 상태 전이 / 다시 시도 횟수 / 다시 분석 준비 테스트
class NewsletterPageTest {

    @Test
    void sameFailureKeepsRetryCountAndDifferentFailureResetsIt() {
        NewsletterPage page = newPage();

        page.markUnreadable();
        assertThat(page.isRetryable()).isTrue();
        assertThat(page.isSkippable()).isFalse();

        // 사용자가 다시 시도 → 같은 사유로 다시 실패
        page.increaseRetryCount();
        page.markUnreadable();
        assertThat(page.getRetryCount()).isEqualTo(1);
        assertThat(page.isRetryable()).isFalse();
        assertThat(page.isSkippable()).isTrue();

        // 사유가 바뀌면 0부터 다시 센다
        page.markOcrFailed();
        assertThat(page.getRetryCount()).isZero();
        assertThat(page.isRetryable()).isTrue();
        assertThat(page.isSkippable()).isFalse();
    }

    @Test
    void successResetsRetryCountAndIsNotRetryable() {
        NewsletterPage page = newPage();
        page.markTranslationFailed();
        page.increaseRetryCount();

        page.completeTranslation("translated", null);

        assertThat(page.getStatus()).isEqualTo(NewsletterPageStatus.SUCCESS);
        assertThat(page.getRetryCount()).isZero();
        assertThat(page.isRetryable()).isFalse();
        assertThat(page.isSkippable()).isFalse();
    }

    @Test
    void resetForReanalysisKeepsOcrAndClearsTranslation() {
        NewsletterPage page = newPage();
        BlockBox box = new BlockBox(0.1, 0.1, 0.5, 0.05);
        page.completeOcr("ocr", "원문", List.of(new NewsletterPageBlock(1, "원문", null, box)));
        page.completeTranslation(
            "translated", List.of(new NewsletterPageBlock(1, "원문", "translated", box)));

        page.resetForReanalysis(true);

        assertThat(page.getStatus()).isEqualTo(NewsletterPageStatus.OCR_DONE);
        assertThat(page.getOriginalText()).isEqualTo("원문");
        assertThat(page.getTranslatedText()).isNull();
        assertThat(page.getBlocks())
            .singleElement()
            .extracting(NewsletterPageBlock::translatedText)
            .isNull();
    }

    @Test
    void resetForReanalysisRestartsImageOcrWhenNoOriginalText() {
        NewsletterPage page = newPage();
        page.markUnreadable();
        page.increaseRetryCount();

        page.resetForReanalysis(true);

        assertThat(page.getStatus()).isEqualTo(NewsletterPageStatus.PENDING);
        assertThat(page.getRetryCount()).isZero();
    }

    @Test
    void resetForReanalysisKeepsPdfUnreadablePageSkipped() {
        NewsletterPage page = newPage();
        page.skip();

        page.resetForReanalysis(false);

        assertThat(page.getStatus()).isEqualTo(NewsletterPageStatus.SKIPPED);
    }

    @Test
    void skipKeepsOriginalTextButClearsTranslation() {
        NewsletterPage page = newPage();
        page.completeOcr("ocr", "원문", null);
        page.markTranslationFailed();

        page.skip();

        assertThat(page.getStatus()).isEqualTo(NewsletterPageStatus.SKIPPED);
        assertThat(page.getOriginalText()).isEqualTo("원문");
        assertThat(page.getTranslatedText()).isNull();
    }

    private NewsletterPage newPage() {
        return NewsletterPage.builder()
            .newsletterId(1L)
            .pageNo(1)
            .fileKey("newsletters/page1.jpg")
            .status(NewsletterPageStatus.PENDING)
            .build();
    }
}
