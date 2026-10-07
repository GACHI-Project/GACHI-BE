package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.CalendarDailyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarEventResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse;
import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse.MarkerType;
import com.gachi.be.domain.calendar.dto.response.CalendarWeeklyResponse;
import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.calendar.service.CalendarQueryService;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
public class CalendarQueryServiceImpl implements CalendarQueryService {

  private final CalendarEventRepository calendarEventRepository;
  private final ChecklistRepository checklistRepository;
  private final NewsletterRepository newsletterRepository;
  private final UserRepository userRepository;
  private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
  private static final String DEFAULT_LANGUAGE = "KO";

  /** 월별 일정 마커 조회. */
  @Override
  @Transactional(readOnly = true)
  public CalendarMonthlyResponse getMonthly(Long userId, int year, int month, String childName) {

    // childName 공백/빈 문자열 → null 정규화
    String normalizedChildName = normalizeChildName(childName);

    // rangeStart: 해당 월 1일 00:00:00 KST
    // rangeEnd: 다음 월 1일 00:00:00 KST
    LocalDate firstDay = LocalDate.of(year, month, 1);
    LocalDate firstDayOfNext = firstDay.plusMonths(1);

    OffsetDateTime rangeStart = firstDay.atStartOfDay().atOffset(KST_OFFSET);
    OffsetDateTime rangeEnd = firstDayOfNext.atStartOfDay().atOffset(KST_OFFSET);

    List<CalendarEvent> events =
        calendarEventRepository.findMonthlyEndpointEvents(
            userId,
            rangeStart,
            rangeEnd,
            firstDay.toString(),
            firstDayOfNext.toString(),
            normalizedChildName);

    List<CalendarMonthlyResponse.MarkerItem> markedDates = new ArrayList<>();
    for (CalendarEvent event : events) {
      Map<LocalDate, EnumSet<MarkerType>> markers = new LinkedHashMap<>();
      LocalDate primaryDate = event.getStartAt().withOffsetSameInstant(KST_OFFSET).toLocalDate();
      if (event.getPeriodStartAt() != null) {
        addMonthlyMarker(markers, event.getPeriodStartAt().substring(0, 10), MarkerType.START);
        addMonthlyMarker(markers, primaryDate, MarkerType.DEADLINE);
      } else if (event.getEndAt() != null) {
        addMonthlyMarker(markers, primaryDate, MarkerType.START);
        addMonthlyMarker(
            markers,
            event.getEndAt().withOffsetSameInstant(KST_OFFSET).toLocalDate(),
            MarkerType.END);
      } else {
        addMonthlyMarker(markers, primaryDate, MarkerType.SINGLE);
      }
      markers.forEach(
          (date, types) -> {
            if (!date.isBefore(firstDay) && date.isBefore(firstDayOfNext)) {
              markedDates.add(
                  new CalendarMonthlyResponse.MarkerItem(
                      date.toString(),
                      event.getChildName(),
                      event.getChildColor(),
                      event.getId(),
                      List.copyOf(types)));
            }
          });
    }
    markedDates.sort(
        Comparator.comparing(CalendarMonthlyResponse.MarkerItem::date)
            .thenComparing(CalendarMonthlyResponse.MarkerItem::eventId));
    log.debug(
        "[CalendarQuery] 월별 마커 조회. userId={}, {}-{}, childName={}, count={}",
        userId,
        year,
        month,
        childName,
        markedDates.size());

    return new CalendarMonthlyResponse(markedDates);
  }

  private void addMonthlyMarker(
      Map<LocalDate, EnumSet<MarkerType>> markers, String date, MarkerType type) {
    addMonthlyMarker(markers, LocalDate.parse(date), type);
  }

  private void addMonthlyMarker(
      Map<LocalDate, EnumSet<MarkerType>> markers, LocalDate date, MarkerType type) {
    markers.computeIfAbsent(date, ignored -> EnumSet.noneOf(MarkerType.class)).add(type);
  }

  private Set<LocalDate> endpointDates(CalendarEvent event) {
    Set<LocalDate> dates = new LinkedHashSet<>();
    dates.add(event.getStartAt().withOffsetSameInstant(KST_OFFSET).toLocalDate());
    if (event.getPeriodStartAt() != null) {
      dates.add(LocalDate.parse(event.getPeriodStartAt().substring(0, 10)));
    } else if (event.getEndAt() != null) {
      dates.add(event.getEndAt().withOffsetSameInstant(KST_OFFSET).toLocalDate());
    }
    return dates;
  }

  /** 주별 일정+체크리스트 조회. */
  @Override
  @Transactional(readOnly = true)
  public CalendarWeeklyResponse getWeekly(Long userId, String date, String childName) {

    String normalizedChildName = normalizeChildName(childName);
    String language = resolveUserLanguage(userId);
    // 오늘 날짜 파싱 (KST)
    LocalDate today = parseLocalDate(date);

    // 이번 주 일요일~토요일 계산 (KST 기준, 일요일 시작)
    // Java DayOfWeek: MONDAY=1 ... SUNDAY=7
    int dayValue = today.getDayOfWeek().getValue();
    LocalDate weekStart = today.minusDays(dayValue % 7);
    LocalDate weekEnd = weekStart.plusDays(6);

    OffsetDateTime rangeStart = weekStart.atStartOfDay().atOffset(KST_OFFSET);
    OffsetDateTime rangeEnd = weekEnd.plusDays(1).atStartOfDay().atOffset(KST_OFFSET); // exclusive

    List<CalendarEvent> events =
        calendarEventRepository.findCalendarEndpointEventsInRange(
            userId,
            rangeStart,
            rangeEnd,
            weekStart.toString(),
            weekEnd.plusDays(1).toString(),
            normalizedChildName);

    Map<LocalDate, List<CalendarEvent>> groupedByDate = new LinkedHashMap<>();
    for (CalendarEvent event : events) {
      for (LocalDate endpoint : endpointDates(event)) {
        if (!endpoint.isBefore(weekStart) && !endpoint.isAfter(weekEnd)) {
          groupedByDate.computeIfAbsent(endpoint, ignored -> new ArrayList<>()).add(event);
        }
      }
    }
    List<LocalDate> sortedDates = buildWeeklySortedDates(today, weekStart, weekEnd);

    List<CalendarWeeklyResponse.DayEvents> days = new ArrayList<>();
    for (LocalDate d : sortedDates) {
      List<CalendarEvent> dayEvents = groupedByDate.get(d);
      if (dayEvents == null || dayEvents.isEmpty()) {
        continue;
      }

      // 각 일정에 체크리스트 붙이기
      List<CalendarEventResponse> eventResponses =
          dayEvents.stream().map(e -> toEventResponse(e, today, language)).toList();

      days.add(
          new CalendarWeeklyResponse.DayEvents(
              d.format(DateTimeFormatter.ISO_LOCAL_DATE), eventResponses));
    }

    log.debug(
        "[CalendarQuery] 주별 조회. userId={}, today={}, weekStart={}, weekEnd={}, dayCount={}",
        userId,
        today,
        weekStart,
        weekEnd,
        days.size());

    return new CalendarWeeklyResponse(
        today.format(DateTimeFormatter.ISO_LOCAL_DATE),
        weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
        weekEnd.format(DateTimeFormatter.ISO_LOCAL_DATE),
        days);
  }

  /** ㅡ날짜별 일정+체크리스트 조회 */
  @Override
  @Transactional(readOnly = true)
  public CalendarDailyResponse getDaily(Long userId, String date, String childName) {

    String normalizedChildName = normalizeChildName(childName);
    LocalDate targetDate = parseLocalDate(date);
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    String language = resolveUserLanguage(userId);

    OffsetDateTime rangeStart = targetDate.atStartOfDay().atOffset(KST_OFFSET);
    OffsetDateTime rangeEnd = targetDate.plusDays(1).atStartOfDay().atOffset(KST_OFFSET);

    List<CalendarEvent> events =
        calendarEventRepository.findCalendarEndpointEventsInRange(
            userId,
            rangeStart,
            rangeEnd,
            targetDate.toString(),
            targetDate.plusDays(1).toString(),
            normalizedChildName);

    List<CalendarEventResponse> eventResponses =
        events.stream()
            .filter(event -> endpointDates(event).contains(targetDate))
            .map(e -> toEventResponse(e, today, language))
            .toList();

    log.debug(
        "[CalendarQuery] 날짜별 조회. userId={}, date={}, count={}",
        userId,
        date,
        eventResponses.size());

    return new CalendarDailyResponse(
        targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE), eventResponses);
  }

  private Map<Long, List<Checklist>> buildChecklistMap(List<CalendarEvent> events) {
    if (events.isEmpty()) return Collections.emptyMap();

    List<Long> eventIds = events.stream().map(CalendarEvent::getId).toList();

    return checklistRepository
        .findByCalendarEventIdInAndType(eventIds, ChecklistType.CHECKLIST)
        .stream()
        .collect(Collectors.groupingBy(Checklist::getCalendarEventId));
  }

  /** 일정 엔티티 → 응답 DTO 변환 (체크리스트 조회 포함). */
  private CalendarEventResponse toEventResponse(
      CalendarEvent event, LocalDate today, String language) {
    // 가정통신문 제목 조회
    String newsletterTitle =
        newsletterRepository
            .findById(event.getNewsletterId())
            .map(n -> n.getTitle() != null ? n.getTitle() : "(제목 없음)")
            .orElse("(삭제된 가정통신문)");

    // 해당 일정에 연결된 CHECKLIST 타입 항목 조회
    List<Checklist> checklists =
        checklistRepository.findByCalendarEventIdAndTypeOrderByIdAsc(
            event.getId(), ChecklistType.CHECKLIST);

    return CalendarEventResponse.of(event, newsletterTitle, checklists, today, language);
  }

  private String resolveUserLanguage(Long userId) {
    return userRepository
        .findById(userId)
        .map(User::getLanguageCode)
        .filter(code -> code != null && !code.isBlank())
        .orElse(DEFAULT_LANGUAGE);
  }

  private String i18nText(Map<String, String> values, String language, String fallback) {
    if (values == null || values.isEmpty()) {
      return fallback;
    }
    String text = values.get(language);
    if (text != null && !text.isBlank()) {
      return text;
    }
    text = values.get(DEFAULT_LANGUAGE);
    if (text != null && !text.isBlank()) {
      return text;
    }
    return fallback;
  }

  /** 주별 날짜 정렬 순서 생성 */
  private List<LocalDate> buildWeeklySortedDates(
      LocalDate today, LocalDate weekStart, LocalDate weekEnd) {

    List<LocalDate> result = new ArrayList<>();

    // 오늘 ~ 토요일
    LocalDate d = today;
    while (!d.isAfter(weekEnd)) {
      result.add(d);
      d = d.plusDays(1);
    }

    // 일요일 ~ 어제
    d = weekStart;
    while (d.isBefore(today)) {
      result.add(d);
      d = d.plusDays(1);
    }

    return result;
  }

  private LocalDate parseLocalDate(String dateStr) {
    try {
      return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (DateTimeParseException e) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          "날짜 형식이 올바르지 않습니다. YYYY-MM-DD 형식으로 입력해주세요. 입력값: " + dateStr,
          e);
    }
  }

  private String normalizeChildName(String childName) {
    return (childName == null || childName.isBlank()) ? null : childName;
  }
}
