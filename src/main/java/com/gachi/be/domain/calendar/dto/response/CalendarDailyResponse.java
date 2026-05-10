package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

/** 캘린더에서 (월별 드러낼 때) 하루 클릭 했을 때 반환됨*/
public record CalendarDailyResponse(

    //조회한 날짜
    String date,
    List<CalendarEventResponse> events
) {}
