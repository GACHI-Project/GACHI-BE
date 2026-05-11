package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.request.CalendarDateUpdateRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarRegisterRequest;
import com.gachi.be.domain.calendar.dto.response.CalendarPreviewResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarRegisterResponse;

/** 캘린더 일정 등록 흐름 서비스 인터페이스 (preview → dates → register). */
public interface CalendarRegisterService {

  CalendarPreviewResponse getPreview(Long userId, Long newsletterId);

  void updateDates(Long userId, Long newsletterId, CalendarDateUpdateRequest request);

  CalendarRegisterResponse register(
      Long userId, Long newsletterId, CalendarRegisterRequest request);
}
