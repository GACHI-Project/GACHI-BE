package com.gachi.be.domain.calendar.api.controller;

import com.gachi.be.domain.calendar.dto.response.CalendarDailyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarWeeklyResponse;
import com.gachi.be.domain.calendar.service.CalendarQueryService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Calendar", description = "캘린더 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendars")
public class CalendarController {

    private final CalendarQueryService calendarQueryService;

    /**별 일정 마커 조회 API. -> 자녀 색 표현 위함*/
    @Operation(
        summary = "월별 일정 마커 조회",
        description = """
          달력에 일정이 있는 날짜 목록(마커)을 반환합니다.
          childName 미전송 시 전체 자녀 일정을 조회합니다.
          날짜 클릭 시에는 GET /calendars/daily를 호출하세요.
          """)
    @GetMapping("/monthly")
    public ApiResponse<CalendarMonthlyResponse> getMonthly(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "연도 (예: 2026)", required = true)
        @RequestParam @Min(2000) @Max(2100) int year,
        @Parameter(description = "월 (1~12)", required = true)
        @RequestParam @Min(1) @Max(12) int month,
        @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.")
        @RequestParam(required = false) String childName) {

        CalendarMonthlyResponse response = calendarQueryService.getMonthly(userId, year, month, childName);
        return ApiResponse.success(SuccessCode.CALENDAR_MONTHLY_SUCCESS, response);
    }

    /**주별 일정+체크리스트 조회 API.*/
    @Operation(
        summary = "주별 일정+체크리스트 조회",
        description = """
          오늘 날짜가 속한 주(일~토)의 전체 일정과 체크리스트를 반환합니다.
          오늘부터 토요일, 그 다음 일요일부터 어제 순으로 정렬됩니다.
          일정 없는 날짜는 응답에 포함되지 않습니다.
          childName 미전송 시 전체 자녀 조회.
          """)
    @GetMapping("/weekly")
    public ApiResponse<CalendarWeeklyResponse> getWeekly(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "오늘 날짜 (YYYY-MM-DD, KST)", required = true)
        @RequestParam String date,
        @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.")
        @RequestParam(required = false) String childName) {

        CalendarWeeklyResponse response = calendarQueryService.getWeekly(userId, date, childName);
        return ApiResponse.success(SuccessCode.CALENDAR_WEEKLY_SUCCESS, response);
    }

    /** 날짜별 일정+체크리스트 조회 API. */
    @Operation(
        summary = "날짜별 일정+체크리스트 조회",
        description = """
          월별 달력에서 날짜 클릭 시 해당 날짜의 일정과 체크리스트를 반환합니다.
          일정이 없으면 빈 리스트를 반환합니다.
          childName 미전송 시 전체 자녀 조회.
          """)
    @GetMapping("/daily")
    public ApiResponse<CalendarDailyResponse> getDaily(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "조회할 날짜 (YYYY-MM-DD, KST)", required = true)
        @RequestParam String date,
        @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.")
        @RequestParam(required = false) String childName) {

        CalendarDailyResponse response = calendarQueryService.getDaily(userId, date, childName);
        return ApiResponse.success(SuccessCode.CALENDAR_DAILY_SUCCESS, response);
    }

    /**일정 삭제 API.*/
    @Operation(
        summary = "일정 삭제",
        description = """
          캘린더 일정을 삭제합니다.
          연결된 체크리스트(type=CHECKLIST)도 함께 삭제됩니다.
          """)
    @DeleteMapping("/{eventId}")
    public ApiResponse<Void> deleteEvent(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "삭제할 일정 ID", required = true) @PathVariable Long eventId) {

        calendarQueryService.deleteEvent(userId, eventId);
        return ApiResponse.success(SuccessCode.CALENDAR_EVENT_DELETED, null);
    }
}
