package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CalendarEventServiceImplTest {
    private final CalendarEventRepository calendarEventRepository =
        mock(CalendarEventRepository.class);
    private final ChecklistRepository checklistRepository = mock(ChecklistRepository.class);

    private final CalendarEventServiceImpl service =
        new CalendarEventServiceImpl(calendarEventRepository, checklistRepository);

    @Test
    void deleteEventDeletesLinkedChecklistsBeforeEvent() {
        CalendarEvent event = mock(CalendarEvent.class);
        when(event.getId()).thenReturn(7L);
        when(calendarEventRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(event));

        service.deleteEvent(1L, 7L);

        InOrder order = inOrder(checklistRepository, calendarEventRepository);
        order.verify(checklistRepository).deleteByCalendarEventId(7L);
        order.verify(calendarEventRepository).delete(event);
    }

    @Test
    void deleteEventThrowsWhenNotOwnedOrMissing() {
        when(calendarEventRepository.findByIdAndUserId(7L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEvent(2L, 7L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CALENDAR_EVENT_NOT_FOUND);
        verify(checklistRepository, never()).deleteByCalendarEventId(any());
        verify(calendarEventRepository, never()).delete(any());
    }
}
