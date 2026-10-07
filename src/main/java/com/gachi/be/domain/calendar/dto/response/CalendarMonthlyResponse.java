package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

/** 월별 달력 화면에서 일정의 시작과 끝 날짜를 표시하기 위한 데이터. */
public record CalendarMonthlyResponse(List<MarkerItem> markedDates) {
  public enum MarkerType {
    START,
    END,
    DEADLINE,
    SINGLE
  }

  public record MarkerItem(
      String date,
      String childName,
      String childColor,
      Long eventId,
      List<MarkerType> markerTypes) {}
}
