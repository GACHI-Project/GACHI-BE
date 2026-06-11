package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.dto.request.CalendarDateUpdateRequest;
import com.gachi.be.domain.calendar.dto.request.CalendarRegisterRequest;
import com.gachi.be.domain.calendar.dto.response.CalendarPreviewResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarRegisterResponse;
import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.calendar.service.CalendarRegisterService;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis에서 임시 일정 데이터 읽기 -> Redis에서 날짜 수정 후 다시 저장 -> calendar_events insert +
 * checklist.calendar_event_id 채우기 → Redis 삭제 날짜만 있는 경우: KST 00:00:00 (= UTC -9:00 기준
 * OffsetDateTime)으로 변환하여 저장 날짜+시간이 있는 경우: KST 시간으로 해석하여 OffsetDateTime(+09:00)으로 변환하여 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarRegisterServiceImpl implements CalendarRegisterService {

  private final CalendarPreviewRedisService previewRedisService;
  private final CalendarEventRepository calendarEventRepository;
  private final ChecklistRepository checklistRepository;
  private final NewsletterRepository newsletterRepository;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String DEFAULT_CHILD_COLOR = "#5B9BD5";
  private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

  /** 캘린더 일정 미리보기 조회. ->Redis에서 preview 데이터를 읽어 반환. TODO: (추후 연결) -> 일정 추출 */
  @Override
  public CalendarPreviewResponse getPreview(Long userId, Long newsletterId) {
    // 가정통신문 소유권 검증
    Newsletter newsletter = findNewsletterAndValidateOwner(userId, newsletterId);

    // Redis에서 preview 데이터 조회
    List<CalendarPreviewEvent> events = previewRedisService.getPreview(newsletterId);
    if (events == null) {
      log.warn("[CalendarRegister] preview 데이터 없음. newsletterId={}", newsletterId);
      throw new BusinessException(
          ErrorCode.CALENDAR_PREVIEW_NOT_FOUND,
          "캘린더 미리보기 데이터가 없습니다. AI 분석 중이거나 데이터가 만료되었을 수 있습니다.");
    }

    log.info(
        "[CalendarRegister] preview 조회. userId={}, newsletterId={}, count={}",
        userId,
        newsletterId,
        events.size());
    return CalendarPreviewResponse.from(sortPreviewEvents(events));
  }

  /** 캘린더 일정 날짜 수정. Redis에서 기존 preview 데이터를 읽어 tempEventId 기준으로 날짜만 교체하고 다시 저장. TTL이 1시간으로 갱신됨. */
  @Override
  public void updateDates(Long userId, Long newsletterId, CalendarDateUpdateRequest request) {
    // 소유권 검증
    findNewsletterAndValidateOwner(userId, newsletterId);

    // 기존 preview 데이터 조회
    List<CalendarPreviewEvent> events = previewRedisService.getPreview(newsletterId);
    if (events == null) {
      throw new BusinessException(ErrorCode.CALENDAR_PREVIEW_NOT_FOUND, "수정할 미리보기 데이터가 없습니다.");
    }

    // tempEventId → correctedDate 맵 구성
    Map<String, LocalDate> correctionMap =
        request.events().stream()
            .collect(Collectors.toMap(e -> e.tempEventId(), e -> e.correctedDate()));

    // 날짜 교체: 수정 요청에 포함된 항목만 extractedDate를 교체, isDateExtracted=true로 변경
    List<CalendarPreviewEvent> updated =
        events.stream()
            .map(
                event -> {
                  // correctionMap에서 꺼낸 값이 LocalDate이므로 변수 타입 변경
                  LocalDate correctedLocalDate = correctionMap.get(event.tempEventId());
                  if (correctedLocalDate != null) {
                    // LocalDate → "YYYY-MM-DD" String으로 변환 후 CalendarPreviewEvent에 저장
                    // CalendarPreviewEvent.extractedDate는 String 타입이므로 변환 필요
                    String correctedDate =
                        correctedLocalDate.format(
                            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                    log.debug(
                        "[CalendarRegister] 날짜 수정. tempEventId={}, {} → {}",
                        event.tempEventId(),
                        event.extractedDate(),
                        correctedDate);
                    return new CalendarPreviewEvent(
                        event.tempEventId(),
                        event.title(),
                        event.titleI18n(),
                        correctedDate,
                        true,
                        event.checklistIds());
                  }
                  return event;
                })
            .toList();

    // 수정된 데이터를 Redis에 다시 저장 (TTL 갱신)
    previewRedisService.savePreview(newsletterId, updated);
    log.info("[CalendarRegister] 날짜 수정 완료. userId={}, newsletterId={}", userId, newsletterId);
  }

  /** 캘린더 일정 등록 (저장하기). */
  @Override
  @Transactional
  public CalendarRegisterResponse register(
      Long userId, Long newsletterId, CalendarRegisterRequest request) {

    // 가정통신문 소유권 검증 + 자녀 스냅샷 정보 조회
    Newsletter newsletter = findNewsletterAndValidateOwner(userId, newsletterId);

    List<CalendarEvent> savedEvents = new ArrayList<>();

    List<CalendarPreviewEvent> previewEvents = previewRedisService.getPreview(newsletterId);
    Map<String, CalendarPreviewEvent> previewByTempId =
        previewEvents == null
            ? Map.of()
            : previewEvents.stream()
                .collect(Collectors.toMap(CalendarPreviewEvent::tempEventId, e -> e, (a, b) -> a));

    // 일정 등록
    for (CalendarRegisterRequest.EventRegister eventReq : request.events()) {
      // external_key = {newsletterId}_{tempEventId}: 중복 등록 방지용 멱등성 키
      String externalKey = newsletterId + "_" + eventReq.tempEventId();

      // 이미 등록된 일정이면 스킵 (멱등성 보장)
      if (calendarEventRepository.findByExternalKey(externalKey).isPresent()) {
        log.warn("[CalendarRegister] 이미 등록된 일정 스킵. externalKey={}", externalKey);
        continue;
      }

      // startAt 파싱: KST 기준 OffsetDateTime으로 변환
      OffsetDateTime startAt = parseToKstOffsetDateTime(eventReq.startAt());

      // endAt 파싱: null이면 단일 날짜 일정
      OffsetDateTime endAt = null;
      if (eventReq.endAt() != null && !eventReq.endAt().isBlank()) {
        endAt = parseToKstOffsetDateTime(eventReq.endAt());
      }

      // CalendarEvent 엔티티 생성
      CalendarEvent calendarEvent =
          CalendarEvent.builder()
              .userId(userId)
              .newsletterId(newsletterId)
              // 가정통신문에 저장된 자녀 스냅샷 그대로 사용 (child_id FK 없음)
              .childName(newsletter.getChildName())
              .childColor(
                  newsletter.getChildColor() != null
                      ? newsletter.getChildColor()
                      : DEFAULT_CHILD_COLOR)
              .title(eventReq.title())
              .titleI18n(
                  resolveCalendarTitleI18n(eventReq, previewByTempId.get(eventReq.tempEventId())))
              .externalKey(externalKey)
              .startAt(startAt)
              .endAt(endAt)
              .build();

      CalendarEvent saved = calendarEventRepository.save(calendarEvent);
      savedEvents.add(saved);
      log.debug(
          "[CalendarRegister] 일정 등록 완료. eventId={}, title={}, startAt={}",
          saved.getId(),
          saved.getTitle(),
          saved.getStartAt());
    }

    // preview 목록 기반으로 체크리스트를 각 일정에 정확하게 연결
    if (!savedEvents.isEmpty()) {
      if (previewEvents != null) {
        linkChecklistsToEvents(newsletterId, savedEvents, previewEvents);
      }
    }

    // Redis preview 데이터 삭제 (등록 완료 후 임시 데이터 정리)
    previewRedisService.deletePreview(newsletterId);

    int count = savedEvents.size();
    log.info(
        "[CalendarRegister] 일정 등록 완료. userId={}, newsletterId={}, count={}",
        userId,
        newsletterId,
        count);

    return new CalendarRegisterResponse(count);
  }

  private Map<String, String> resolveCalendarTitleI18n(
      CalendarRegisterRequest.EventRegister eventReq, CalendarPreviewEvent preview) {
    if (preview == null || preview.titleI18n() == null || preview.titleI18n().isEmpty()) {
      return Map.of();
    }
    // 사용자가 preview 제목을 수정했다면 AI가 만든 다국어 제목은 더 이상 같은 의미라고 보장할 수 없습니다.
    if (!eventReq.title().equals(preview.title())) {
      return Map.of();
    }
    return preview.titleI18n();
  }

  /** CHECKLIST 타입 항목들을 등록된 캘린더 일정에 연결. */
  private void linkChecklistsToEvents(
      Long newsletterId,
      List<CalendarEvent> savedEvents,
      List<CalendarPreviewEvent> previewEvents) {
    // tempEventId → savedCalendarEvent 맵
    Map<String, CalendarEvent> eventByTempId =
        savedEvents.stream()
            .collect(
                Collectors.toMap(e -> extractTempId(e.getExternalKey(), newsletterId), e -> e));

    for (CalendarPreviewEvent preview : previewEvents) {
      if (preview.checklistIds() == null || preview.checklistIds().isEmpty()) {
        continue; // 이 일정에 연결할 체크리스트 없음
      }

      CalendarEvent linkedEvent = eventByTempId.get(preview.tempEventId());
      if (linkedEvent == null) {
        // 중복 등록 스킵된 일정이면 eventByTempId에 없을 수 있음
        log.debug(
            "[CalendarRegister] tempEventId에 해당하는 등록 일정 없음. tempEventId={}", preview.tempEventId());
        continue;
      }

      // checklistIds 목록에 해당하는 체크리스트에 calendar_event_id 연결
      List<Checklist> checklists = checklistRepository.findAllById(preview.checklistIds());
      checklists.forEach(c -> c.linkToCalendarEvent(linkedEvent.getId()));
      checklistRepository.saveAll(checklists);

      log.debug(
          "[CalendarRegister] 체크리스트 {}개 → 일정({}) 연결. tempEventId={}",
          checklists.size(),
          linkedEvent.getId(),
          preview.tempEventId());
    }
  }

  // external_key에서 tempEventId 추출.
  private String extractTempId(String externalKey, Long newsletterId) {
    String prefix = newsletterId + "_";
    return externalKey.startsWith(prefix) ? externalKey.substring(prefix.length()) : externalKey;
  }

  private List<CalendarPreviewEvent> sortPreviewEvents(List<CalendarPreviewEvent> events) {
    return events.stream().sorted(Comparator.comparing(this::previewSortKey)).toList();
  }

  private LocalDateTime previewSortKey(CalendarPreviewEvent event) {
    String extractedDate = event.extractedDate();
    if (extractedDate == null || extractedDate.isBlank()) {
      return LocalDateTime.MAX;
    }

    String normalized = extractedDate.trim();
    try {
      if (normalized.length() == 10) {
        return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
      }
      try {
        return OffsetDateTime.parse(normalized).atZoneSameInstant(KST).toLocalDateTime();
      } catch (DateTimeParseException ignored) {
        return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      }
    } catch (DateTimeParseException e) {
      log.debug(
          "[CalendarRegister] preview 날짜 정렬 기준 파싱 실패. tempEventId={}, extractedDate={}",
          event.tempEventId(),
          extractedDate);
      return LocalDateTime.MAX;
    }
  }

  /** 날짜/시간 문자열을 KST 기준 OffsetDateTime으로 변환 */
  private OffsetDateTime parseToKstOffsetDateTime(String dateStr) {
    try {
      if (dateStr.length() == 10) {
        // "YYYY-MM-DD" 형식: KST 당일 00:00:00
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return LocalDateTime.of(date, LocalTime.MIDNIGHT).atOffset(KST_OFFSET);
      } else {
        // "YYYY-MM-DDTHH:mm:ss" 형식: KST 해당 시각
        LocalDateTime dateTime =
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return dateTime.atOffset(KST_OFFSET);
      }
    } catch (DateTimeParseException e) {
      log.error("[CalendarRegister] 날짜 파싱 실패. value={}, error={}", dateStr, e.getMessage());
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, "날짜 형식이 올바르지 않습니다. 입력값: " + dateStr, e);
    }
  }

  /** 가정통신문 조회 + 소유권 검증. */
  private Newsletter findNewsletterAndValidateOwner(Long userId, Long newsletterId) {
    Newsletter newsletter =
        newsletterRepository
            .findById(newsletterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));
    if (!newsletter.getUserId().equals(userId)) {
      throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
    }
    return newsletter;
  }
}
