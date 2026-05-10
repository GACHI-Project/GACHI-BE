package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.response.CalendarDailyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarWeeklyResponse;

/** 캘린더 조회, 삭제*/
public interface CalendarQueryService {

    /** 월별 일정 마커 조회 */
    CalendarMonthlyResponse getMonthly(Long userId, int year, int month, String childName);

    /** 주별 일정+체크리스트 조회 */
    CalendarWeeklyResponse getWeekly(Long userId, String date, String childName);

    /** 날짜별 일정+체크리스트 조회 */
    CalendarDailyResponse getDaily(Long userId, String date, String childName);

    /** 일정 삭제 (연결 체크리스트 포함) */
    void deleteEvent(Long userId, Long eventId);
}
