package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 마감/체크리스트/주간 요약처럼 사용자의 별도 요청 없이 생성되어야 하는 알림을 만든다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final CalendarEventRepository calendarEventRepository;
  private final ChecklistRepository checklistRepository;
  private final NewsletterRepository newsletterRepository;
  private final UserRepository userRepository;
  private final ChildRepository childRepository;
  private final NotificationService notificationService;
  private final Clock clock;

  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void createDailyReminders() {
    LocalDate today = LocalDate.now(clock);
    createDeadlineReminders(today.plusDays(1));
    createChecklistReminders(today.plusDays(3), 3);
    createChecklistReminders(today.plusDays(1), 1);
  }

  @Scheduled(cron = "0 30 9 * * SUN", zone = "Asia/Seoul")
  public void createWeeklySummaries() {
    LocalDate today = LocalDate.now(clock);
    createWeeklySummaries(today.minusDays(6), today.plusDays(1));
  }

  public void createDeadlineReminders(LocalDate targetDate) {
    TimeRange range = dayRange(targetDate);
    List<CalendarEvent> events =
        calendarEventRepository.findByStartAtGreaterThanEqualAndStartAtLessThan(
            range.start(), range.end());

    for (CalendarEvent event : events) {
      Long childId = resolveChildId(event.getUserId(), event.getChildName());
      Map<String, Object> payload = payload();
      payload.put("calendarEventId", event.getId());
      payload.put("newsletterId", event.getNewsletterId());
      payload.put("targetDate", targetDate.toString());
      putIfPresent(payload, "childName", event.getChildName());

      notificationService.createNotification(
          event.getUserId(),
          new NotificationCreateCommand(
              NotificationType.DEADLINE_REMINDER,
              event.getTitle() + " 마감 D-1",
              "내일 마감이에요",
              payload,
              "deadline:" + event.getId() + ":" + targetDate,
              NotificationLevel.URGENT,
              childId,
              event.getChildName()));
    }
  }

  public void createChecklistReminders(LocalDate targetDate, int daysBefore) {
    createTodoReminders(targetDate, daysBefore);
    createLinkedChecklistReminders(targetDate, daysBefore);
  }

  private void createTodoReminders(LocalDate targetDate, int daysBefore) {
    List<Checklist> todos =
        checklistRepository.findByTypeAndCompletedFalseAndTargetDate(
            ChecklistType.TODO, targetDate);
    for (Checklist checklist : todos) {
      Newsletter newsletter = findNewsletter(checklist.getNewsletterId());
      String childName = newsletter != null ? newsletter.getChildName() : null;
      Long childId = resolveChildId(checklist.getUserId(), childName);

      Map<String, Object> payload = payload();
      payload.put("checklistId", checklist.getId());
      payload.put("newsletterId", checklist.getNewsletterId());
      payload.put("targetDate", targetDate.toString());
      putIfPresent(payload, "childName", childName);

      notificationService.createNotification(
          checklist.getUserId(),
          new NotificationCreateCommand(
              NotificationType.CHECKLIST_DUE,
              "미완료 할 일이 있어요",
              checklist.getContent(),
              payload,
              "todo:" + checklist.getId() + ":d-" + daysBefore + ":" + targetDate,
              NotificationLevel.IMPORTANT,
              childId,
              childName));
    }
  }

  private void createLinkedChecklistReminders(LocalDate targetDate, int daysBefore) {
    TimeRange range = dayRange(targetDate);
    List<CalendarEvent> events =
        calendarEventRepository.findByStartAtGreaterThanEqualAndStartAtLessThan(
            range.start(), range.end());
    if (events.isEmpty()) {
      return;
    }

    Map<Long, CalendarEvent> eventById =
        events.stream().collect(Collectors.toMap(CalendarEvent::getId, Function.identity()));
    List<Checklist> checklists =
        checklistRepository.findIncompleteChecklistItemsByCalendarEventIds(
            eventById.keySet().stream().toList());

    for (Checklist checklist : checklists) {
      CalendarEvent event = eventById.get(checklist.getCalendarEventId());
      if (event == null) {
        continue;
      }
      Long childId = resolveChildId(checklist.getUserId(), event.getChildName());

      Map<String, Object> payload = payload();
      payload.put("checklistId", checklist.getId());
      payload.put("calendarEventId", event.getId());
      payload.put("newsletterId", checklist.getNewsletterId());
      payload.put("targetDate", targetDate.toString());
      putIfPresent(payload, "childName", event.getChildName());

      notificationService.createNotification(
          checklist.getUserId(),
          new NotificationCreateCommand(
              NotificationType.CHECKLIST_DUE,
              "미완료 할 일이 있어요",
              checklist.getContent(),
              payload,
              "checklist:" + checklist.getId() + ":d-" + daysBefore + ":" + targetDate,
              NotificationLevel.IMPORTANT,
              childId,
              event.getChildName()));
    }
  }

  public void createWeeklySummaries(LocalDate rangeStartDate, LocalDate rangeEndDate) {
    OffsetDateTime rangeStart = rangeStartDate.atStartOfDay(KST).toOffsetDateTime();
    OffsetDateTime rangeEnd = rangeEndDate.atStartOfDay(KST).toOffsetDateTime();
    List<User> users = userRepository.findAllByStatus(UserStatus.ACTIVE);

    for (User user : users) {
      long newsletterCount =
          newsletterRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
              user.getId(), rangeStart, rangeEnd);
      long incompleteCount = checklistRepository.countByUserIdAndCompletedFalse(user.getId());

      Map<String, Object> payload = payload();
      payload.put("rangeStart", rangeStartDate.toString());
      payload.put("rangeEnd", rangeEndDate.minusDays(1).toString());
      payload.put("newsletterCount", newsletterCount);
      payload.put("incompleteChecklistCount", incompleteCount);

      notificationService.createNotification(
          user.getId(),
          new NotificationCreateCommand(
              NotificationType.WEEKLY_SUMMARY,
              "이번 주 요약이 도착했어요",
              "이번 주 가정통신문 " + newsletterCount + "개와 미완료 할 일 " + incompleteCount + "개를 확인해보세요",
              payload,
              "weekly-summary:" + user.getId() + ":" + rangeStartDate,
              NotificationLevel.NORMAL,
              null,
              null));
    }
  }

  private TimeRange dayRange(LocalDate targetDate) {
    return new TimeRange(
        targetDate.atStartOfDay(KST).toOffsetDateTime(),
        targetDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
  }

  private Newsletter findNewsletter(Long newsletterId) {
    if (newsletterId == null) {
      return null;
    }
    return newsletterRepository.findById(newsletterId).orElse(null);
  }

  private Long resolveChildId(Long userId, String childName) {
    if (childName == null || childName.isBlank()) {
      return null;
    }
    return childRepository
        .findFirstByUserIdAndNameAndDeletedAtIsNull(userId, childName)
        .map(child -> child.getId())
        .orElse(null);
  }

  private Map<String, Object> payload() {
    return new LinkedHashMap<>();
  }

  private void putIfPresent(Map<String, Object> payload, String key, Object value) {
    if (value != null) {
      payload.put(key, value);
    }
  }

  private record TimeRange(OffsetDateTime start, OffsetDateTime end) {}
}
