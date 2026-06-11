package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarRegisterServiceImplTest {
  private final CalendarPreviewRedisService previewRedisService =
      mock(CalendarPreviewRedisService.class);
  private final CalendarEventRepository calendarEventRepository =
      mock(CalendarEventRepository.class);
  private final ChecklistRepository checklistRepository = mock(ChecklistRepository.class);
  private final NewsletterRepository newsletterRepository = mock(NewsletterRepository.class);

  private final CalendarRegisterServiceImpl service =
      new CalendarRegisterServiceImpl(
          previewRedisService, calendarEventRepository, checklistRepository, newsletterRepository);

  @Test
  void getPreviewSortsEventsByExtractedDateAscending() {
    Long userId = 1L;
    Long newsletterId = 10L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                preview("evt-3", "마감 일정", "2026-06-20"),
                preview("evt-1", "빠른 일정", "2026-06-01"),
                preview("evt-4", "시간 포함 일정", "2026-06-03T09:00:00"),
                preview("evt-5", "KST 오전 일정", "2026-06-03T08:00:00+09:00"),
                preview("evt-6", "UTC 저녁 일정", "2026-06-03T12:00:00Z"),
                preview("evt-2", "중간 일정", "2026-06-02")));

    var response = service.getPreview(userId, newsletterId);

    assertThat(response.events())
        .extracting(CalendarPreviewEvent::tempEventId, CalendarPreviewEvent::extractedDate)
        .containsExactly(
            tuple("evt-1", "2026-06-01"),
            tuple("evt-2", "2026-06-02"),
            tuple("evt-5", "2026-06-03T08:00:00+09:00"),
            tuple("evt-4", "2026-06-03T09:00:00"),
            tuple("evt-6", "2026-06-03T12:00:00Z"),
            tuple("evt-3", "2026-06-20"));
  }

  @Test
  void getPreviewPlacesMissingOrInvalidDatesLast() {
    Long userId = 2L;
    Long newsletterId = 20L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                preview("evt-invalid", "날짜 확인 필요", "날짜 미확정"),
                preview("evt-early", "빠른 일정", "2026-06-01"),
                preview("evt-empty", "빈 날짜", null)));

    var response = service.getPreview(userId, newsletterId);

    assertThat(response.events())
        .extracting(CalendarPreviewEvent::tempEventId)
        .containsExactly("evt-early", "evt-invalid", "evt-empty");
  }

  private CalendarPreviewEvent preview(String tempEventId, String title, String extractedDate) {
    return new CalendarPreviewEvent(
        tempEventId, title, extractedDate, extractedDate != null, List.of());
  }

  private Newsletter newsletter(Long userId) {
    return Newsletter.builder()
        .userId(userId)
        .fileKey("newsletter/test.png")
        .fileHash("hash-" + userId)
        .status(NewsletterStatus.COMPLETED)
        .language("KO")
        .build();
  }
}
