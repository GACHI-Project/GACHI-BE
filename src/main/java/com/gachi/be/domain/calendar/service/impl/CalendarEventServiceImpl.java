package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.calendar.service.CalendarEventService;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캘린더 일정 삭제.
 * 체크리스트를 먼저 명시적으로 삭제한 뒤 일정을 삭제. 이미 발송된 알림은 X
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarEventServiceImpl implements CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final ChecklistRepository checklistRepository;

    /** 캘린더 일정 삭제 + 연결된 체크리스트 삭제. */
    @Override
    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        // 소유권 검증 포함 조회 (타인 일정이면 존재하지 않는 것처럼 404)
        CalendarEvent event =
            calendarEventRepository
                .findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CALENDAR_EVENT_NOT_FOUND));

        // 연결된 체크리스트 먼저 삭제 (FK SET NULL로 고아 체크리스트가 남는 것 방지)
        checklistRepository.deleteByCalendarEventId(event.getId());

        calendarEventRepository.delete(event);

        log.info(
            "[CalendarEvent] 일정 삭제 완료. userId={}, eventId={}, newsletterId={}",
            userId,
            eventId,
            event.getNewsletterId());
    }
}
