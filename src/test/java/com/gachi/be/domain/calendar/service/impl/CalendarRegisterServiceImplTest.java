package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.dto.request.CalendarDateUpdateRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarRegisterRequest;
import com.gachi.be.domain.calendar.dto.response.CalendarEventResponse;
import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

  @Test
  void registerPreservesPreviewTimeWithLegacyDateOnlyRequest() {
    Long userId = 3L;
    Long newsletterId = 30L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                new CalendarPreviewEvent(
                    "evt-1",
                    "행사",
                    java.util.Map.of(),
                    "2026-09-18",
                    true,
                    List.of(),
                    "2026-09-18T11:00:00",
                    "2026-09-18T14:00:00",
                    null,
                    false)));
    when(calendarEventRepository.save(org.mockito.ArgumentMatchers.any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.register(
        userId,
        newsletterId,
        new CalendarRegisterRequest(
            List.of(new CalendarRegisterRequest.EventRegister("evt-1", "행사", "2026-09-18", null))));

    ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
    verify(calendarEventRepository).save(captor.capture());
    CalendarEvent event = captor.getValue();
    assertThat(event.getStartAt().toString()).isEqualTo("2026-09-18T11:00+09:00");
    assertThat(event.getEndAt().toString()).isEqualTo("2026-09-18T14:00+09:00");
    assertThat(event.isAllDay()).isFalse();
  }

  @Test
  void registerKeepsDeadlineAsOneEventWithPeriodStart() {
    Long userId = 4L;
    Long newsletterId = 40L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                new CalendarPreviewEvent(
                    "evt-1",
                    "신청 마감",
                    java.util.Map.of(),
                    "2026-09-28",
                    true,
                    List.of(),
                    "2026-09-28T18:00:00",
                    null,
                    "2026-09-10T10:00:00",
                    false)));
    when(calendarEventRepository.save(org.mockito.ArgumentMatchers.any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        service.register(
            userId,
            newsletterId,
            new CalendarRegisterRequest(
                List.of(
                    new CalendarRegisterRequest.EventRegister(
                        "evt-1", "신청 마감", "2026-09-28", null))));

    ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
    verify(calendarEventRepository).save(captor.capture());
    assertThat(response.registeredCount()).isEqualTo(1);
    assertThat(captor.getValue().getStartAt().toString()).isEqualTo("2026-09-28T18:00+09:00");
    assertThat(captor.getValue().getPeriodStartAt().toString()).isEqualTo("2026-09-10T10:00+09:00");
    assertThat(captor.getValue().getEndAt()).isNull();
    CalendarEventResponse calendarResponse =
        CalendarEventResponse.of(
            captor.getValue(), "가정통신문", List.of(), LocalDate.of(2026, 9, 1), "KO");
    assertThat(calendarResponse.periodStartAt()).isEqualTo("2026-09-10T10:00+09:00");
    assertThat(calendarResponse.startAt()).isEqualTo("2026-09-28T18:00+09:00");
    assertThat(calendarResponse.allDay()).isFalse();
  }

  @Test
  void updatingPreviewDatePreservesTimeAndMovesScheduleEnd() {
    Long userId = 5L;
    Long newsletterId = 50L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                new CalendarPreviewEvent(
                    "evt-1",
                    "행사",
                    java.util.Map.of(),
                    "2026-09-18",
                    true,
                    List.of(),
                    "2026-09-18T11:00:00",
                    "2026-09-18T14:00:00",
                    null,
                    false)));

    service.updateDates(
        userId,
        newsletterId,
        new CalendarDateUpdateRequest(
            List.of(
                new CalendarDateUpdateRequest.EventDateUpdate(
                    "evt-1", LocalDate.of(2026, 9, 19)))));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<CalendarPreviewEvent>> captor = ArgumentCaptor.forClass(List.class);
    verify(previewRedisService)
        .savePreview(org.mockito.ArgumentMatchers.eq(newsletterId), captor.capture());
    CalendarPreviewEvent updated = captor.getValue().get(0);
    assertThat(updated.extractedDate()).isEqualTo("2026-09-19");
    assertThat(updated.startAt()).isEqualTo("2026-09-19T11:00:00");
    assertThat(updated.endAt()).isEqualTo("2026-09-19T14:00:00");
    assertThat(updated.allDay()).isFalse();
  }

  @Test
  void registerPreservesDateOnlyAuxiliaryValuesWithoutShowingMidnight() {
    Long userId = 6L;
    Long newsletterId = 60L;
    when(newsletterRepository.findById(newsletterId)).thenReturn(Optional.of(newsletter(userId)));
    when(previewRedisService.getPreview(newsletterId))
        .thenReturn(
            List.of(
                new CalendarPreviewEvent(
                    "evt-1",
                    "행사",
                    java.util.Map.of(),
                    "2026-09-18",
                    true,
                    List.of(),
                    "2026-09-18T11:00:00",
                    "2026-09-18",
                    null,
                    false),
                new CalendarPreviewEvent(
                    "evt-2",
                    "신청 마감",
                    java.util.Map.of(),
                    "2026-09-28",
                    true,
                    List.of(),
                    "2026-09-28T18:00:00",
                    null,
                    "2026-09-10",
                    false)));
    when(calendarEventRepository.save(org.mockito.ArgumentMatchers.any(CalendarEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.register(
        userId,
        newsletterId,
        new CalendarRegisterRequest(
            List.of(
                new CalendarRegisterRequest.EventRegister("evt-1", "행사", "2026-09-18", null),
                new CalendarRegisterRequest.EventRegister("evt-2", "신청 마감", "2026-09-28", null))));

    ArgumentCaptor<CalendarEvent> captor = ArgumentCaptor.forClass(CalendarEvent.class);
    verify(calendarEventRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    CalendarEvent event = captor.getAllValues().get(0);
    CalendarEventResponse eventResponse =
        CalendarEventResponse.of(event, "가정통신문", List.of(), LocalDate.of(2026, 9, 1), "KO");
    assertThat(eventResponse.endAt()).isEqualTo("2026-09-18");
    assertThat(eventResponse.endAllDay()).isTrue();

    CalendarEvent deadline = captor.getAllValues().get(1);
    CalendarEventResponse deadlineResponse =
        CalendarEventResponse.of(deadline, "가정통신문", List.of(), LocalDate.of(2026, 9, 1), "KO");
    assertThat(deadlineResponse.periodStartAt()).isEqualTo("2026-09-10");
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
