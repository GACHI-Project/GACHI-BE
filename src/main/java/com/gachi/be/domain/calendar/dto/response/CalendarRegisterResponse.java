package com.gachi.be.domain.calendar.dto.response;

public record CalendarRegisterResponse(

    // 실제로 calendar_events에 insert된 일정 수. 중복(external_key 충돌)은 제외
    int registeredCount
) {}
