package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.ExtractedItem;
import com.gachi.be.domain.newsletter.pipeline.NewsletterAiAnalyzer.AiAnalysisResult;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsletterAiAnalyzerTest {

  @Mock private AiNewsletterClient aiNewsletterClient;
  @Mock private ChecklistRepository checklistRepository;
  @Mock private NewsletterRepository newsletterRepository;

  @Captor private ArgumentCaptor<List<Checklist>> checklistsCaptor;

  @InjectMocks private NewsletterAiAnalyzer newsletterAiAnalyzer;

  @Test
  void analyzeUsesAiTitleSummaryAndSavesItems() {
    Long newsletterId = 10L;
    Newsletter newsletter =
        Newsletter.builder()
            .userId(20L)
            .fileKey("newsletter.pdf")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    ExtractedItem item =
        new ExtractedItem(
            "reminder",
            "준비물 확인",
            null,
            "2026-05-25",
            "Asia/Seoul",
            "체육복을 준비해 주세요.",
            "confirmed",
            0.91,
            false,
            null);

    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter));
    when(aiNewsletterClient.analyze("원문", "번역문", "KO", List.of()))
        .thenReturn(new AnalysisResponse("AI 제목", "AI 요약", List.of(item), Map.of()));

    AiAnalysisResult result = newsletterAiAnalyzer.analyze(newsletterId, "원문", "번역문", "KO");

    assertThat(result.title()).isEqualTo("AI 제목");
    assertThat(result.summary()).isEqualTo("AI 요약");

    verify(checklistRepository).saveAll(checklistsCaptor.capture());

    List<Checklist> savedItems = checklistsCaptor.getValue();
    assertThat(savedItems).hasSize(1);
    assertThat(savedItems.get(0).getNewsletterId()).isEqualTo(newsletterId);
    assertThat(savedItems.get(0).getUserId()).isEqualTo(20L);
    assertThat(savedItems.get(0).getContent()).isEqualTo("준비물 확인");
    assertThat(savedItems.get(0).getDetail()).isEqualTo("체육복을 준비해 주세요.");
    assertThat(savedItems.get(0).getTargetDate()).isEqualTo(LocalDate.parse("2026-05-25"));
    assertThat(savedItems.get(0).getTargetDateLabel()).isEqualTo("5월 25일");
  }

  @Test
  void analyzeFallsBackWhenAiTitleSummaryAreBlank() {
    Long newsletterId = 11L;
    Newsletter newsletter =
        Newsletter.builder()
            .userId(21L)
            .fileKey("newsletter.pdf")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter));
    when(aiNewsletterClient.analyze("가정통신문 제목\n본문입니다.", "번역 요약 대상", "KO", List.of()))
        .thenReturn(new AnalysisResponse("  ", "  ", List.of(), Map.of()));

    AiAnalysisResult result =
        newsletterAiAnalyzer.analyze(newsletterId, "가정통신문 제목\n본문입니다.", "번역 요약 대상", "KO");

    assertThat(result.title()).isEqualTo("가정통신문 제목");
    assertThat(result.summary()).isEqualTo("번역 요약 대상");
    verify(checklistRepository, org.mockito.Mockito.never()).saveAll(anyList());
  }

  @Test
  void analyzeFallsBackOnlySummaryWhenAiSummaryIsBlank() {
    Long newsletterId = 12L;
    Newsletter newsletter =
        Newsletter.builder()
            .userId(22L)
            .fileKey("newsletter.pdf")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter));
    when(aiNewsletterClient.analyze("원문 제목\n본문입니다.", "번역 요약 대상", "KO", List.of()))
        .thenReturn(new AnalysisResponse("AI 제목", " ", List.of(), Map.of()));

    AiAnalysisResult result =
        newsletterAiAnalyzer.analyze(newsletterId, "원문 제목\n본문입니다.", "번역 요약 대상", "KO");

    assertThat(result.title()).isEqualTo("AI 제목");
    assertThat(result.summary()).isEqualTo("번역 요약 대상");
    verify(checklistRepository, org.mockito.Mockito.never()).saveAll(anyList());
  }

  @Test
  void analyzeFallsBackOnlyTitleWhenAiTitleIsBlank() {
    Long newsletterId = 13L;
    Newsletter newsletter =
        Newsletter.builder()
            .userId(23L)
            .fileKey("newsletter.pdf")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter));
    when(aiNewsletterClient.analyze("원문 제목\n본문입니다.", "번역 요약 대상", "KO", List.of()))
        .thenReturn(new AnalysisResponse("", "AI 요약", List.of(), Map.of()));

    AiAnalysisResult result =
        newsletterAiAnalyzer.analyze(newsletterId, "원문 제목\n본문입니다.", "번역 요약 대상", "KO");

    assertThat(result.title()).isEqualTo("원문 제목");
    assertThat(result.summary()).isEqualTo("AI 요약");
    verify(checklistRepository, org.mockito.Mockito.never()).saveAll(anyList());
  }
}
