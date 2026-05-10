package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

/**주별 캘린더(일~토) 전체 일정과 각 일정에 연결된 체크리스트를 반환.*/
public record CalendarWeeklyResponse(

    // 오늘 날짜
    String today,

    //이번주 시작(일)
    String weekStart,

    //이번주 끝(토)
    String weekEnd,

    //일정이 있는 날짜별 데이터 목록.
    List<DayEvents> days
) {
    public record DayEvents(
        String date,
        List<CalendarEventResponse> events
    ) {}
}
