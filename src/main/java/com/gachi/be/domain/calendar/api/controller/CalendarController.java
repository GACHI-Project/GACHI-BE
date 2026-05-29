package com.gachi.be.domain.calendar.api.controller;

import com.gachi.be.domain.calendar.dto.response.CalendarDailyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarWeeklyResponse;
import com.gachi.be.domain.calendar.dto.response.SchoolScheduleCalendarResponse;
import com.gachi.be.domain.calendar.service.CalendarQueryService;
import com.gachi.be.domain.calendar.service.SchoolScheduleQueryService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.code.SuccessCode;
import com.gachi.be.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
  private static final long MAX_SCHOOL_SCHEDULE_RANGE_DAYS = 366L;

  private final CalendarQueryService calendarQueryService;
  private final SchoolScheduleQueryService schoolScheduleQueryService;

  /** 별 일정 마커 조회 API. -> 자녀 색 표현 위함 */
  @Operation(
      summary = "월별 일정 마커 조회",
      description =
          """
          달력에 일정이 있는 날짜 목록(마커)을 반환합니다.
          childName 미전송 시 전체 자녀 일정을 조회합니다.
          날짜 클릭 시에는 GET /calendars/daily를 호출하세요.
          """)
  @GetMapping("/monthly")
  public ApiResponse<CalendarMonthlyResponse> getMonthly(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "연도 (예: 2026)", required = true) @RequestParam @Min(2000) @Max(2100)
          int year,
      @Parameter(description = "월 (1~12)", required = true) @RequestParam @Min(1) @Max(12)
          int month,
      @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.") @RequestParam(required = false)
          String childName) {

    CalendarMonthlyResponse response =
        calendarQueryService.getMonthly(userId, year, month, childName);
    return ApiResponse.success(SuccessCode.CALENDAR_MONTHLY_SUCCESS, response);
  }

  /** 주별 일정+체크리스트 조회 API. */
  @Operation(
      summary = "주별 일정+체크리스트 조회",
      description =
          """
          오늘 날짜가 속한 주(일~토)의 전체 일정과 체크리스트를 반환합니다.
          오늘부터 토요일, 그 다음 일요일부터 어제 순으로 정렬됩니다.
          일정 없는 날짜는 응답에 포함되지 않습니다.
          childName 미전송 시 전체 자녀 조회.
          """)
  @GetMapping("/weekly")
  public ApiResponse<CalendarWeeklyResponse> getWeekly(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "오늘 날짜 (YYYY-MM-DD, KST)", required = true) @RequestParam
          String date,
      @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.") @RequestParam(required = false)
          String childName) {

    CalendarWeeklyResponse response = calendarQueryService.getWeekly(userId, date, childName);
    return ApiResponse.success(SuccessCode.CALENDAR_WEEKLY_SUCCESS, response);
  }

  /** 날짜별 일정+체크리스트 조회 API. */
  @Operation(
      summary = "날짜별 일정+체크리스트 조회",
      description =
          """
          월별 달력에서 날짜 클릭 시 해당 날짜의 일정과 체크리스트를 반환합니다.
          일정이 없으면 빈 리스트를 반환합니다.
          childName 미전송 시 전체 자녀 조회.
          """)
  @GetMapping("/daily")
  public ApiResponse<CalendarDailyResponse> getDaily(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회할 날짜 (YYYY-MM-DD, KST)", required = true) @RequestParam
          String date,
      @Parameter(description = "자녀 이름 필터. 미전송 시 전체 자녀.") @RequestParam(required = false)
          String childName) {

    CalendarDailyResponse response = calendarQueryService.getDaily(userId, date, childName);
    return ApiResponse.success(SuccessCode.CALENDAR_DAILY_SUCCESS, response);
  }

  /** 자녀 학교별 NEIS 학사일정 조회 API. */
  @Operation(
      summary = "자녀 학교 학사일정 조회",
      description =
          """
          로그인 사용자의 자녀 학교 식별값으로 NEIS 학사일정을 조회합니다.
          같은 학교를 다니는 자녀는 officeCode + schoolCode 기준으로 하나의 학교 그룹에 묶입니다.
          """)
  @GetMapping("/school-schedules")
  public ApiResponse<SchoolScheduleCalendarResponse> getSchoolSchedules(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "조회 시작일 (YYYY-MM-DD)", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @Parameter(description = "조회 종료일 (YYYY-MM-DD)", required = true)
          @RequestParam
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate) {

    validateSchoolScheduleRange(fromDate, toDate);
    SchoolScheduleCalendarResponse response =
        schoolScheduleQueryService.getSchoolSchedules(userId, fromDate, toDate);
    return ApiResponse.success(SuccessCode.CALENDAR_SCHOOL_SCHEDULE_SUCCESS, response);
  }

  private void validateSchoolScheduleRange(LocalDate fromDate, LocalDate toDate) {
    if (fromDate.isAfter(toDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작일은 종료일보다 이전이어야 합니다.");
    }
    if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_SCHOOL_SCHEDULE_RANGE_DAYS) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최대 1년까지 조회 가능합니다.");
    }
  }
}
