package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.newsletter.entity.enums.DateCandidateExtractionType;
import com.gachi.be.domain.newsletter.pipeline.NewsletterDateCandidateExtractor.ExtractedDateCandidate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsletterDateCandidateExtractorTest {

  private final NewsletterDateCandidateExtractor extractor =
      new NewsletterDateCandidateExtractor(
          Clock.fixed(Instant.parse("2026-05-06T00:00:00Z"), ZoneId.of("Asia/Seoul")));
  private final LocalDate referenceDate = LocalDate.of(2026, 5, 6);

  @Test
  void extractsExplicitDateCandidates() {
    String text = "체험학습은 2026-05-15에 진행하며, 신청서는 5월 10일까지 제출해 주세요. 설명회는 5/12입니다.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    assertThat(candidates)
        .extracting(ExtractedDateCandidate::originalText)
        .containsExactly("2026-05-15", "5월 10일", "5/12");
    assertThat(candidates)
        .extracting(ExtractedDateCandidate::normalizedDate)
        .containsExactly(
            LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12));
    assertThat(candidates)
        .extracting(ExtractedDateCandidate::extractionType)
        .containsOnly(DateCandidateExtractionType.REGEX);
  }

  @Test
  void doesNotExtractRelativeDateCandidatesInFirstScope() {
    String text = "오늘 안내문을 확인하고 내일까지 회신해 주세요. 다음 주에는 상담 주간을 운영합니다.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    assertThat(candidates).isEmpty();
  }

  @Test
  void usesConfiguredClockWhenReferenceDateIsNull() {
    String text = "상담은 5/10에 진행됩니다.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, null);

    assertThat(candidates)
        .extracting(ExtractedDateCandidate::normalizedDate)
        .containsExactly(LocalDate.of(2026, 5, 10));
  }

  @Test
  void storesEachDateExpressionAsIndependentCandidate() {
    String text = "장학금 신청은 5월 10일 접수 시작, 5월 13일 접수 종료입니다. 동의서는 5월 15일까지 제출해 주세요.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    assertThat(candidates)
        .extracting(ExtractedDateCandidate::originalText)
        .containsExactly("5월 10일", "5월 13일", "5월 15일");
    assertThat(candidates)
        .extracting(ExtractedDateCandidate::normalizedDate)
        .containsExactly(
            LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 15));
  }

  @Test
  void returnsEmptyListWhenTextHasNoDateCandidate() {
    String text = "학교생활 안전 수칙을 안내드립니다. 등하교 때 주변을 잘 살펴 주세요.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    assertThat(candidates).isEmpty();
  }

  @Test
  void skipsInvalidCalendarDates() {
    String text = "2월 31일은 실제 달력 날짜가 아니므로 후보로 저장하지 않습니다.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    assertThat(candidates).isEmpty();
  }

  @Test
  void keepsSourceOffsetsForLaterMatching() {
    String text = "가정통신문 안내\n5월 10일까지 신청서를 제출해 주세요.";

    List<ExtractedDateCandidate> candidates = extractor.extract(text, referenceDate);

    ExtractedDateCandidate candidate = candidates.get(0);
    assertThat(text.substring(candidate.startOffset(), candidate.endOffset()))
        .isEqualTo(candidate.originalText());
  }
}
