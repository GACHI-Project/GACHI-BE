package com.gachi.be.domain.calendar.service;

public interface CalendarEventService {

    /** 캘린더 일정 삭제. 연결된 체크리스트도 함께 삭제. */
    void deleteEvent(Long userId, Long eventId);
}
