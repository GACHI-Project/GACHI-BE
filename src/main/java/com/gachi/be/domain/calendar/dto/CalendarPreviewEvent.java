package com.gachi.be.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/** Redis에 저장되고 조회되는 캘린더 일정 미리보기 단건 DTO. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarPreviewEvent(
    /** 임시 식별자. preview → PATCH dates → POST calendar 흐름에서 일정을 식별하는 키. */
    String tempEventId,

    /** 일정 제목. AI가 추출하거나 사용자가 수정한 값. */
    String title,

    /** 알림 렌더링용 일정 제목 다국어 map. 사용자가 제목을 직접 수정하면 기존 title을 우선한다. */
    Map<String, String> titleI18n,

    /** 기존 클라이언트용 기준 날짜. YYYY-MM-DD 형식. */
    String extractedDate,

    /** 날짜 추출 성공 여부. false면 사용자가 직접 수정해야 함. */
    boolean isDateExtracted,

    // 이 일정에 속하는 체크리스트 ID 목록
    List<Long> checklistIds,

    /** 일정의 기준 날짜 또는 날짜+시각. */
    String startAt,

    /** 행사 종료 날짜 또는 날짜+시각. 마감 일정에서는 null. */
    String endAt,

    /** 마감 일정에 연결된 접수 시작 날짜 또는 날짜+시각. 별도 일정이 아님. */
    String periodStartAt,

    /** startAt에 시각이 명시되지 않았는지 여부. */
    boolean allDay) {

  public CalendarPreviewEvent {
    if (startAt == null) {
      startAt = extractedDate;
    }
    allDay = startAt != null && startAt.length() == 10;
  }

  public CalendarPreviewEvent(
      String tempEventId,
      String title,
      Map<String, String> titleI18n,
      String extractedDate,
      boolean isDateExtracted,
      List<Long> checklistIds) {
    this(
        tempEventId,
        title,
        titleI18n,
        extractedDate,
        isDateExtracted,
        checklistIds,
        null,
        null,
        null,
        false);
  }

  public CalendarPreviewEvent(
      String tempEventId,
      String title,
      String extractedDate,
      boolean isDateExtracted,
      List<Long> checklistIds) {
    this(tempEventId, title, Map.of(), extractedDate, isDateExtracted, checklistIds);
  }
}
