package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewsletterContentHasherTest {

  private final NewsletterContentHasher hasher = new NewsletterContentHasher();

  @Test
  void hashReturnsSameValueWhenOnlyWhitespaceAndPunctuationDiffer() {
    String first = "와글와글 베이커리 신청 안내\n제출 기한: 2026.06.20\n준비물: 앞치마";
    String second = "와글와글 베이커리 신청 안내 - 제출 기한 2026-06-20 / 준비물 앞치마";

    assertThat(hasher.hash(first)).isPresent();
    assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second));
  }

  @Test
  void hashReturnsEmptyWhenTextIsTooShort() {
    assertThat(hasher.hash("신청 안내")).isEmpty();
  }
}
