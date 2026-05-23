package com.gachi.be.domain.newsletter.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
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
}
