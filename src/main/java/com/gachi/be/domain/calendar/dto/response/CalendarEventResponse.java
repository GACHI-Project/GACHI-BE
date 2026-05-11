package com.gachi.be.domain.calendar.dto.response;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.checklist.entity.Checklist;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

public record CalendarEventResponse(
    Long eventId,
    String title,
    String startAt,
    String endAt,
    int dDay,
    String childName,
    String calendarColor,
    String newsletterTitle,
    List<ChecklistItem> checklists) {
  public record ChecklistItem(
      Long checklistId, String content, String detail, boolean isCompleted) {
    public static ChecklistItem from(Checklist c) {
      return new ChecklistItem(c.getId(), c.getContent(), c.getDetail(), c.isCompleted());
    }
  }

  public static CalendarEventResponse of(
      CalendarEvent event, String newsletterTitle, List<Checklist> checklists, LocalDate today) {

    ZoneOffset kst = ZoneOffset.ofHours(9);
    String startAtStr = event.getStartAt().withOffsetSameInstant(kst).toString();

    // null이면 null 반환
    String endAtStr =
        event.getEndAt() != null ? event.getEndAt().withOffsetSameInstant(kst).toString() : null;

    // D-day 계산: startAt의 KST 날짜 기준
    LocalDate eventDate = event.getStartAt().withOffsetSameInstant(kst).toLocalDate();
    // dDay > 0: 미래(앞으로 N일), dDay == 0: 오늘, dDay < 0: 과거
    int dDay = (int) (eventDate.toEpochDay() - today.toEpochDay());

    List<ChecklistItem> checklistItems = checklists.stream().map(ChecklistItem::from).toList();

    return new CalendarEventResponse(
        event.getId(),
        event.getTitle(),
        startAtStr,
        endAtStr,
        dDay,
        event.getChildName(),
        event.getChildColor(),
        newsletterTitle,
        checklistItems);
  }
}
