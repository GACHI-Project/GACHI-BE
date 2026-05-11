package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

/** 월별 달력 화면에서 일정이 있는 날짜에 자녀 색깔 점(마커)을 표시하기 위한 데이터. 날짜 목록만 반환하고 일정 상세는 포함 X */
public record CalendarMonthlyResponse(List<String> markedDates) {}
