package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.response.SchoolMealCalendarResponse;
import java.time.LocalDate;

public interface SchoolMealQueryService {
  SchoolMealCalendarResponse getSchoolMeals(Long userId, LocalDate fromDate, LocalDate toDate);
}
