package com.gachi.be.domain.calendar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**Redis에 저장되고 조회되는 캘린더 일정 미리보기 단건 DTO.*/
@JsonIgnoreProperties(ignoreUnknown = true)
public record CalendarPreviewEvent(
    /** 임시 식별자. preview → PATCH dates → POST calendar 흐름에서 일정을 식별하는 키. */
    String tempEventId,

    /** 일정 제목. AI가 추출하거나 사용자가 수정한 값. */
    String title,

    /** AI가 추출한 날짜(+시간). ISO 8601 형식.*/
    String extractedDate,

    /** 날짜 추출 성공 여부. false면 사용자가 직접 수정해야 함. */
    boolean isDateExtracted,

    // 이 일정에 속하는 체크리스트 ID 목록
    List<Long> checklistIds
) {}
