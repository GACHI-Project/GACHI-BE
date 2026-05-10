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
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redis에서 임시 일정 데이터 읽기 -> Redis에서 날짜 수정 후 다시 저장 -> calendar_events insert + checklist.calendar_event_id 채우기 → Redis 삭제
 * 날짜만 있는 경우: KST 00:00:00 (= UTC -9:00 기준 OffsetDateTime)으로 변환하여 저장
 * 날짜+시간이 있는 경우: KST 시간으로 해석하여 OffsetDateTime(+09:00)으로 변환하여 저장
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

    /** 캘린더 일정 미리보기 조회.
     * ->Redis에서 preview 데이터를 읽어 반환.
     * TODO: (추후 연결) -> 일정 추출 */
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

        log.info("[CalendarRegister] preview 조회. userId={}, newsletterId={}, count={}",
            userId, newsletterId, events.size());
        return CalendarPreviewResponse.from(events);
    }
    /** 캘린더 일정 날짜 수정.
     * Redis에서 기존 preview 데이터를 읽어 tempEventId 기준으로 날짜만 교체하고 다시 저장.
     * TTL이 1시간으로 갱신됨.
     */
    @Override
    public void updateDates(Long userId, Long newsletterId, CalendarDateUpdateRequest request) {
        // 소유권 검증
        findNewsletterAndValidateOwner(userId, newsletterId);

        // 기존 preview 데이터 조회
        List<CalendarPreviewEvent> events = previewRedisService.getPreview(newsletterId);
        if (events == null) {
            throw new BusinessException(ErrorCode.CALENDAR_PREVIEW_NOT_FOUND,
                "수정할 미리보기 데이터가 없습니다.");
        }

        // tempEventId → correctedDate 맵 구성
        Map<String, String> correctionMap = request.events().stream()
            .collect(Collectors.toMap(
                CalendarDateUpdateRequest.EventDateUpdate::tempEventId,
                CalendarDateUpdateRequest.EventDateUpdate::correctedDate));

        // 날짜 교체: 수정 요청에 포함된 항목만 extractedDate를 교체, isDateExtracted=true로 변경
        List<CalendarPreviewEvent> updated = events.stream()
            .map(event -> {
                String correctedDate = correctionMap.get(event.tempEventId());
                if (correctedDate != null) {
                    // 날짜만 수정 (시간 정보 없음). 등록 시 KST 00:00:00으로 처리됨.
                    log.debug("[CalendarRegister] 날짜 수정. tempEventId={}, {} → {}",
                        event.tempEventId(), event.extractedDate(), correctedDate);
                    return new CalendarPreviewEvent(
                        event.tempEventId(),
                        event.title(),
                        correctedDate,
                        true
                    );
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
            CalendarEvent calendarEvent = CalendarEvent.builder()
                .userId(userId)
                .newsletterId(newsletterId)
                // 가정통신문에 저장된 자녀 스냅샷 그대로 사용 (child_id FK 없음)
                .childName(newsletter.getChildName())
                .childColor(newsletter.getChildColor() != null
                    ? newsletter.getChildColor()
                    : DEFAULT_CHILD_COLOR)
                .title(eventReq.title())
                .externalKey(externalKey)
                .startAt(startAt)
                .endAt(endAt)
                .build();

            CalendarEvent saved = calendarEventRepository.save(calendarEvent);
            savedEvents.add(saved);
            log.debug("[CalendarRegister] 일정 등록 완료. eventId={}, title={}, startAt={}",
                saved.getId(), saved.getTitle(), saved.getStartAt());
        }

        // 등록된 일정이 있으면 CHECKLIST 타입 체크리스트에 calendar_event_id 연결
        if (!savedEvents.isEmpty()) {
            linkChecklistsToEvents(newsletterId, savedEvents);
        }

        // Redis preview 데이터 삭제 (등록 완료 후 임시 데이터 정리)
        previewRedisService.deletePreview(newsletterId);

        int count = savedEvents.size();
        log.info("[CalendarRegister] 일정 등록 완료. userId={}, newsletterId={}, count={}",
            userId, newsletterId, count);

        return new CalendarRegisterResponse(count);
    }
    /** CHECKLIST 타입 항목들을 등록된 캘린더 일정에 연결.*/
    private void linkChecklistsToEvents(Long newsletterId, List<CalendarEvent> savedEvents) {
        // 해당 가정통신문의 CHECKLIST 타입 항목만 조회 (TODO 제외)
        List<Checklist> checklists = checklistRepository
            .findByNewsletterIdAndTypeOrderByIdAsc(newsletterId, ChecklistType.CHECKLIST);

        if (checklists.isEmpty()) {
            log.debug("[CalendarRegister] 연결할 CHECKLIST 항목 없음. newsletterId={}", newsletterId);
            return;
        }

        if (savedEvents.size() == 1) {
            // 일정이 1개이면 모든 체크리스트를 해당 일정에 연결
            Long eventId = savedEvents.get(0).getId();
            checklists.forEach(c -> c.linkToCalendarEvent(eventId));
        } else {
            // 일정이 여러 개이면 균등 분배
            // 인덱스 기반으로 체크리스트를 순서대로 일정에 나눠서 연결
            int eventCount = savedEvents.size();
            for (int i = 0; i < checklists.size(); i++) {
                Long eventId = savedEvents.get(i % eventCount).getId();
                checklists.get(i).linkToCalendarEvent(eventId);
            }
        }

        checklistRepository.saveAll(checklists);
        log.debug("[CalendarRegister] CHECKLIST {}개 → 일정 {}개 연결 완료. newsletterId={}",
            checklists.size(), savedEvents.size(), newsletterId);
    }

    /** 날짜/시간 문자열을 KST 기준 OffsetDateTime으로 변환*/
    private OffsetDateTime parseToKstOffsetDateTime(String dateStr) {
        try {
            if (dateStr.length() == 10) {
                // "YYYY-MM-DD" 형식: KST 당일 00:00:00
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                return LocalDateTime.of(date, LocalTime.MIDNIGHT).atOffset(KST_OFFSET);
            } else {
                // "YYYY-MM-DDTHH:mm:ss" 형식: KST 해당 시각
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return dateTime.atOffset(KST_OFFSET);
            }
        } catch (DateTimeParseException e) {
            log.error("[CalendarRegister] 날짜 파싱 실패. value={}, error={}", dateStr, e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "날짜 형식이 올바르지 않습니다. 입력값: " + dateStr);
        }
    }

    /** 가정통신문 조회 + 소유권 검증. */
    private Newsletter findNewsletterAndValidateOwner(Long userId, Long newsletterId) {
        Newsletter newsletter = newsletterRepository.findById(newsletterId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND));
        if (!newsletter.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NEWSLETTER_NOT_FOUND);
        }
        return newsletter;
    }
}
