package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NewsletterAiAnalyzerTest {

  @Mock private AiNewsletterClient aiNewsletterClient;
  @Mock private ChecklistRepository checklistRepository;
  @Mock private CalendarPreviewRedisService calendarPreviewRedisService;
  @Mock private NewsletterRepository newsletterRepository;

  @Captor private ArgumentCaptor<List<Checklist>> checklistsCaptor;
  @Captor private ArgumentCaptor<List<CalendarPreviewEvent>> previewEventsCaptor;

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
    when(checklistRepository.saveAll(anyList()))
        .thenAnswer(
            invocation -> {
              List<Checklist> checklists = invocation.getArgument(0);
              ReflectionTestUtils.setField(checklists.get(0), "id", 501L);
              return checklists;
            });

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

    verify(calendarPreviewRedisService)
        .savePreview(eq(newsletterId), previewEventsCaptor.capture());
    List<CalendarPreviewEvent> previewEvents = previewEventsCaptor.getValue();
    assertThat(previewEvents).hasSize(1);
    assertThat(previewEvents.get(0).tempEventId()).isEqualTo("ai_evt_1");
    assertThat(previewEvents.get(0).title()).isEqualTo("준비물 확인");
    assertThat(previewEvents.get(0).extractedDate()).isEqualTo("2026-05-25");
    assertThat(previewEvents.get(0).isDateExtracted()).isTrue();
    assertThat(previewEvents.get(0).checklistIds()).containsExactly(501L);
  }

  @Test
  void analyzeSkipsCalendarPreviewWhenDateIsNotConfirmed() {
    Long newsletterId = 14L;
    Newsletter newsletter =
        Newsletter.builder()
            .userId(24L)
            .fileKey("newsletter.pdf")
            .fileHash("hash")
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();

    ExtractedItem item =
        new ExtractedItem(
            "deadline",
            "신청서 제출",
            null,
            "2026-05-25",
            "Asia/Seoul",
            "체험학습 3일 전까지 신청서를 제출해 주세요.",
            "ambiguous",
            0.7,
            true,
            "체험학습 날짜를 확인해 주세요.");

    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter));
    when(aiNewsletterClient.analyze("원문", "번역문", "KO", List.of()))
        .thenReturn(new AnalysisResponse("AI 제목", "AI 요약", List.of(item), Map.of()));
    when(checklistRepository.saveAll(anyList()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    newsletterAiAnalyzer.analyze(newsletterId, "원문", "번역문", "KO");

    verify(calendarPreviewRedisService).deletePreview(newsletterId);
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
