package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.response.ElementaryTimetableCalendarResponse;
import java.time.LocalDate;

public interface ElementaryTimetableQueryService {
  ElementaryTimetableCalendarResponse getElementaryTimetables(
      Long userId, LocalDate fromDate, LocalDate toDate);
}
