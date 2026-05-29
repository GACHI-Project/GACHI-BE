package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.response.SchoolScheduleCalendarResponse;
import java.time.LocalDate;

public interface SchoolScheduleQueryService {
  SchoolScheduleCalendarResponse getSchoolSchedules(
      Long userId, LocalDate fromDate, LocalDate toDate);
}
