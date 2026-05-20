package com.gachi.be.domain.newsletter.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import org.junit.jupiter.api.Test;

class NewsletterTest {

  @Test
  void failWithSnapshotPreservesTextExtractionResult() {
    Newsletter newsletter =
        Newsletter.builder()
            .userId(1L)
            .fileKey("newsletters/sample.png")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    newsletter.failWithSnapshot(
        "ocr text",
        "original text",
        "translated text",
        "AI_SERVER",
        "ExternalApiException: timeout");

    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.FAILED);
    assertThat(newsletter.getOcrText()).isEqualTo("ocr text");
    assertThat(newsletter.getOriginalText()).isEqualTo("original text");
    assertThat(newsletter.getTranslatedText()).isEqualTo("translated text");
    assertThat(newsletter.getFailureStage()).isEqualTo("AI_SERVER");
    assertThat(newsletter.getFailureReason()).contains("timeout");
  }

  @Test
  void prepareRetryClearsFailureFieldsAndDerivedSummary() {
    Newsletter newsletter =
        Newsletter.builder()
            .userId(1L)
            .fileKey("newsletters/sample.png")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();
    newsletter.complete("ocr text", "original text", null, "title", "summary");
    newsletter.fail("AI_SERVER", "timeout");

    newsletter.prepareRetry();

    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.PENDING);
    assertThat(newsletter.getFailureStage()).isNull();
    assertThat(newsletter.getFailureReason()).isNull();
    assertThat(newsletter.getTitle()).isNull();
    assertThat(newsletter.getSummary()).isNull();
  }
}
