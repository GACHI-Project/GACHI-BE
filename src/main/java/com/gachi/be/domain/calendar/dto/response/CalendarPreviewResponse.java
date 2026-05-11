package com.gachi.be.domain.calendar.dto.response;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import java.util.List;

/** 팝업에 표시할 AI 추출 일정 목록. Redis에서 읽어 그대로 반환. 날짜 추출 실패 항목(isDateExtracted=false)도 포함하여 반환. */
public record CalendarPreviewResponse(List<CalendarPreviewEvent> events) {

  public static CalendarPreviewResponse from(List<CalendarPreviewEvent> events) {
    return new CalendarPreviewResponse(events);
  }
}
