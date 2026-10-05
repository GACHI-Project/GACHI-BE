package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse.MarkerType;
import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarQueryServiceImplTest {
  private final CalendarEventRepository calendarEventRepository =
      mock(CalendarEventRepository.class);
  private final CalendarQueryServiceImpl service =
      new CalendarQueryServiceImpl(
          calendarEventRepository,
          mock(ChecklistRepository.class),
          mock(NewsletterRepository.class),
          mock(UserRepository.class));

  @Test
  void monthlyReturnsOnlyDeadlinePeriodEndpointsAcrossMonths() {
    CalendarEvent event = event(1L, "2026-09-28T18:00:00+09:00", null, "2026-08-31T10:00:00+09:00");
    when(calendarEventRepository.findMonthlyEndpointEvents(
            eq(7L), any(), any(), any(), any(), eq("민수")))
        .thenReturn(List.of(event));

    var august = service.getMonthly(7L, 2026, 8, "민수").markedDates();
    var september = service.getMonthly(7L, 2026, 9, "민수").markedDates();

    assertThat(august).hasSize(1);
    assertThat(august.get(0).date()).isEqualTo("2026-08-31");
    assertThat(august.get(0).eventId()).isEqualTo(1L);
    assertThat(august.get(0).markerTypes()).containsExactly(MarkerType.START);
    assertThat(september).hasSize(1);
    assertThat(september.get(0).date()).isEqualTo("2026-09-28");
    assertThat(september.get(0).markerTypes()).containsExactly(MarkerType.DEADLINE);
  }

  @Test
  void monthlyMergesSameDayDeadlineStartAndDeadline() {
    CalendarEvent event = event(6L, "2026-09-18T18:00:00+09:00", null, "2026-09-18T10:00:00+09:00");
    when(calendarEventRepository.findMonthlyEndpointEvents(
            eq(7L), any(), any(), any(), any(), eq(null)))
        .thenReturn(List.of(event));

    var markers = service.getMonthly(7L, 2026, 9, null).markedDates();

    assertThat(markers).hasSize(1);
    assertThat(markers.get(0).markerTypes()).containsExactly(MarkerType.START, MarkerType.DEADLINE);
  }

  @Test
  void monthlyReturnsOnlyScheduleEndpointsAndMergesSameDayRoles() {
    CalendarEvent period =
        event(2L, "2026-09-01T11:00:00+09:00", "2026-09-18T14:00:00+09:00", null);
    CalendarEvent sameDay =
        event(3L, "2026-09-18T09:00:00+09:00", "2026-09-18T17:00:00+09:00", null);
    CalendarEvent single = event(4L, "2026-09-20T10:00:00+09:00", null, null);
    when(calendarEventRepository.findMonthlyEndpointEvents(
            eq(7L), any(), any(), any(), any(), eq(null)))
        .thenReturn(List.of(period, sameDay, single));

    var markers = service.getMonthly(7L, 2026, 9, null).markedDates();

    assertThat(markers).hasSize(4);
    assertThat(markers.get(0).date()).isEqualTo("2026-09-01");
    assertThat(markers.get(0).markerTypes()).containsExactly(MarkerType.START);
    assertThat(markers.get(1).date()).isEqualTo("2026-09-18");
    assertThat(markers.get(1).markerTypes()).containsExactly(MarkerType.END);
    assertThat(markers.get(2).date()).isEqualTo("2026-09-18");
    assertThat(markers.get(2).markerTypes()).containsExactly(MarkerType.START, MarkerType.END);
    assertThat(markers.get(3).markerTypes()).containsExactly(MarkerType.SINGLE);
  }

  @Test
  void dailyAndWeeklyIncludePeriodStartAndDeadlineButNotMiddleDay() {
    CalendarEvent event = event(5L, "2026-09-18T18:00:00+09:00", null, "2026-09-10T10:00:00+09:00");
    when(calendarEventRepository.findCalendarEndpointEventsInRange(
            eq(7L), any(), any(), any(), any(), eq(null)))
        .thenReturn(List.of(event));

    assertThat(service.getDaily(7L, "2026-09-10", null).events()).hasSize(1);
    assertThat(service.getDaily(7L, "2026-09-14", null).events()).isEmpty();
    assertThat(service.getDaily(7L, "2026-09-18", null).events()).hasSize(1);
    assertThat(service.getWeekly(7L, "2026-09-10", null).days())
        .extracting(day -> day.date())
        .containsExactly("2026-09-10");
    assertThat(service.getWeekly(7L, "2026-09-18", null).days())
        .extracting(day -> day.date())
        .containsExactly("2026-09-18");
  }

  private CalendarEvent event(Long id, String start, String end, String periodStart) {
    CalendarEvent event = mock(CalendarEvent.class);
    when(event.getId()).thenReturn(id);
    when(event.getStartAt()).thenReturn(OffsetDateTime.parse(start));
    when(event.getEndAt()).thenReturn(end == null ? null : OffsetDateTime.parse(end));
    when(event.getPeriodStartAt()).thenReturn(periodStart);
    when(event.getChildName()).thenReturn("민수");
    when(event.getChildColor()).thenReturn("#12AB34");
    return event;
  }
}
