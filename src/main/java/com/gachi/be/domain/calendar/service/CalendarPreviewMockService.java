package com.gachi.be.domain.calendar.service;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.dto.request.CalendarPreviewMockRequest;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 캘린더 preview 더미 데이터 주입
 *
 * <p>AI 파이프라인 연결 전 테스트용. -> 추후 삭제 예정
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarPreviewMockService {

  private final CalendarPreviewRedisService previewRedisService;
  private final ChecklistRepository checklistRepository;
  private final NewsletterRepository newsletterRepository;

  /** Redis에 preview 더미 데이터 주입. tempEventId는 자동 생성 */
  public void injectMockPreview(
      Long userId, Long newsletterId, CalendarPreviewMockRequest request) {

    // 소유권 검증
    Newsletter newsletter =
        newsletterRepository
            .findById(newsletterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));
    if (!newsletter.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
    }

    List<CalendarPreviewMockRequest.MockEvent> mockEvents = request.events();

    // checklistIds 자동 배분이 필요한지 확인
    boolean needsAutoAssign =
        mockEvents.stream().anyMatch(e -> e.checklistIds() == null || e.checklistIds().isEmpty());

    // 자동 배분 필요 시 DB에서 CHECKLIST 타입 항목 ID 조회
    List<Long> allChecklistIds = List.of();
    if (needsAutoAssign) {
      allChecklistIds =
          checklistRepository
              .findByNewsletterIdAndTypeOrderByIdAsc(newsletterId, ChecklistType.CHECKLIST)
              .stream()
              .map(Checklist::getId)
              .toList();
    }

    // CalendarPreviewEvent 목록 생성
    List<CalendarPreviewEvent> previewEvents = new ArrayList<>();
    int eventCount = mockEvents.size();

    for (int i = 0; i < eventCount; i++) {
      CalendarPreviewMockRequest.MockEvent mockEvent = mockEvents.get(i);

      // tempEventId 자동 생성
      String tempEventId = "mock_evt_" + (i + 1);

      // checklistIds 결정
      List<Long> checklistIds;
      if (mockEvent.checklistIds() != null && !mockEvent.checklistIds().isEmpty()) {
        // 명시적으로 지정된 경우 그대로 사용
        checklistIds = mockEvent.checklistIds();
      } else {
        // 자동 균등 배분: i번째 일정에 해당하는 체크리스트 할당
        checklistIds = new ArrayList<>();
        for (int j = i; j < allChecklistIds.size(); j += eventCount) {
          checklistIds.add(allChecklistIds.get(j));
        }
      }

      // extractedDate null이면 isDateExtracted=false
      boolean isDateExtracted = mockEvent.extractedDate() != null;

      previewEvents.add(
          new CalendarPreviewEvent(
              tempEventId,
              mockEvent.title(),
              mockEvent.extractedDate(),
              isDateExtracted,
              checklistIds));

      log.debug(
          "[MockPreview] 이벤트 생성. tempEventId={}, title={}, checklistIds={}",
          tempEventId,
          mockEvent.title(),
          checklistIds);
    }

    // Redis에 저장
    previewRedisService.savePreview(newsletterId, previewEvents);

    log.info(
        "[MockPreview] Redis 주입 완료. userId={}, newsletterId={}, eventCount={}",
        userId,
        newsletterId,
        previewEvents.size());
  }
}
