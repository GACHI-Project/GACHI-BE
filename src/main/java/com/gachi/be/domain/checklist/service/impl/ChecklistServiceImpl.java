package com.gachi.be.domain.checklist.service.impl;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.dto.request.ChecklistCompleteRequest;
import com.gachi.be.domain.checklist.dto.response.ChecklistTodayItem;
import com.gachi.be.domain.checklist.dto.response.ChecklistTodayResponse;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.checklist.service.ChecklistService;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.util.I18nTextResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistServiceImpl implements ChecklistService {

  private final ChecklistRepository checklistRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final NewsletterRepository newsletterRepository;
  private final UserRepository userRepository;
  private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
  private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

  /** 오늘 마감 미완료 체크리스트 조회. */
  @Override
  @Transactional(readOnly = true)
  public ChecklistTodayResponse getTodayChecklists(Long userId) {
    String language = resolveUserLanguage(userId);

    // KST 오늘 날짜 범위 계산
    LocalDate todayKst = LocalDate.now(KST_ZONE);
    OffsetDateTime rangeStart = todayKst.atStartOfDay().atOffset(KST_OFFSET);
    OffsetDateTime rangeEnd = todayKst.plusDays(1).atStartOfDay().atOffset(KST_OFFSET);

    // 오늘 날짜에 해당하는 calendar_events 조회
    List<CalendarEvent> todayEvents =
        calendarEventRepository.findEventsInRange(
            userId, rangeStart, rangeEnd, null); // null = 전체 자녀

    if (todayEvents.isEmpty()) {
      log.debug("[ChecklistService] 오늘 마감 일정 없음. userId={}, date={}", userId, todayKst);
      return ChecklistTodayResponse.of(List.of());
    }

    // 오늘 일정 ID 목록 추출
    List<Long> todayEventIds = todayEvents.stream().map(CalendarEvent::getId).toList();

    // 해당 일정들에 연결된 미완료 CHECKLIST 조회
    List<Checklist> checklists =
        checklistRepository.findIncompleteChecklistsByCalendarEventIds(userId, todayEventIds);

    if (checklists.isEmpty()) {
      log.debug("[ChecklistService] 오늘 마감 미완료 체크리스트 없음. userId={}", userId);
      return ChecklistTodayResponse.of(List.of());
    }

    // 캘린더 일정 ID → CalendarEvent 맵 (자녀 이름 조회용)
    Map<Long, CalendarEvent> eventMap =
        todayEvents.stream().collect(Collectors.toMap(CalendarEvent::getId, e -> e));

    List<Long> newsletterIds =
        checklists.stream().map(Checklist::getNewsletterId).distinct().toList();

    Map<Long, String> titleByNewsletterId =
        newsletterRepository.findAllById(newsletterIds).stream()
            .collect(
                Collectors.toMap(
                    n -> n.getId(), n -> n.getTitle() != null ? n.getTitle() : "(제목 없음)"));

    // 체크리스트 → DTO 변환 (newsletter 제목 + 자녀 이름 조회)
    List<ChecklistTodayItem> items =
        checklists.stream()
            .map(
                c -> {
                  // 가정통신문 제목 조회
                  String newsletterTitle =
                      titleByNewsletterId.getOrDefault(c.getNewsletterId(), "(삭제된 가정통신문)");

                  // 자녀 이름: 연결된 일정에서 가져옴
                  CalendarEvent linkedEvent = eventMap.get(c.getCalendarEventId());
                  String childName = linkedEvent != null ? linkedEvent.getChildName() : null;

                  return ChecklistTodayItem.of(c, newsletterTitle, childName, language);
                })
            .toList();

    log.debug("[ChecklistService] 오늘 마감 체크리스트 조회. userId={}, count={}", userId, items.size());
    return ChecklistTodayResponse.of(items);
  }

  /** 체크리스트 완료 상태 토글. */
  @Override
  @Transactional
  public void updateComplete(Long userId, Long checklistId, ChecklistCompleteRequest request) {

    Checklist checklist =
        checklistRepository
            .findByIdAndUserId(checklistId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND));

    if (checklist.getType() == ChecklistType.TODO) {
      throw new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND);
    }

    checklist.updateCompleted(request.isCompleted());

    log.debug(
        "[ChecklistService] 완료 처리. userId={}, checklistId={}, isCompleted={}",
        userId,
        checklistId,
        request.isCompleted());
  }

  /** 체크리스트 삭제. */
  @Override
  @Transactional
  public void deleteChecklist(Long userId, Long checklistId) {

    Checklist checklist =
        checklistRepository
            .findByIdAndUserId(checklistId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND));

    if (checklist.getType() == ChecklistType.TODO) {
      throw new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND);
    }

    checklistRepository.delete(checklist);

    log.info(
        "[ChecklistService] 체크리스트 삭제. userId={}, checklistId={}, type={}",
        userId,
        checklistId,
        checklist.getType());
  }
    private String resolveUserLanguage(Long userId) {
        return userRepository
            .findById(userId)
            .map(User::getLanguageCode)
            .filter(code -> code != null && !code.isBlank())
            .orElse(I18nTextResolver.DEFAULT_LANGUAGE);
    }
}
