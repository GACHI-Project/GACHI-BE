package com.gachi.be.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationSchedulerTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final LocalDate RANGE_START = LocalDate.of(2026, 6, 1);
  private static final LocalDate RANGE_END = LocalDate.of(2026, 6, 8);

  private CalendarEventRepository calendarEventRepository;
  private ChecklistRepository checklistRepository;
  private NewsletterRepository newsletterRepository;
  private UserRepository userRepository;
  private ChildRepository childRepository;
  private NotificationService notificationService;
  private NotificationScheduler notificationScheduler;

  @BeforeEach
  void setUp() {
    calendarEventRepository = mock(CalendarEventRepository.class);
    checklistRepository = mock(ChecklistRepository.class);
    newsletterRepository = mock(NewsletterRepository.class);
    userRepository = mock(UserRepository.class);
    childRepository = mock(ChildRepository.class);
    notificationService = mock(NotificationService.class);
    notificationScheduler =
        new NotificationScheduler(
            calendarEventRepository,
            checklistRepository,
            newsletterRepository,
            userRepository,
            childRepository,
            notificationService,
            CLOCK);
  }

  @Test
  void createWeeklySummariesSkipsWhenAllCountsAreZero() {
    User user = activeUser(1L);
    when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));
    stubWeeklyCounts(user.getId(), 0L, 0L, 0L);

    notificationScheduler.createWeeklySummaries(RANGE_START, RANGE_END);

    verify(notificationService, never()).createNotification(anyLong(), any());
  }

  @Test
  void createWeeklySummariesUsesCalendarCountAndOmitsZeroCountTexts() {
    User user = activeUser(2L);
    when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));
    stubWeeklyCounts(user.getId(), 1L, 0L, 0L);

    notificationScheduler.createWeeklySummaries(RANGE_START, RANGE_END);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);
    verify(notificationService).createNotification(eq(user.getId()), commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();
    assertThat(command.type()).isEqualTo(NotificationType.WEEKLY_SUMMARY);
    assertThat(command.level()).isEqualTo(NotificationLevel.NORMAL);
    assertThat(command.title()).isEqualTo("이번 주 요약이 도착했어요");
    assertThat(command.body()).isEqualTo("이번 주 일정 1개를 확인해보세요");
    assertThat(command.body()).doesNotContain("가정통신문 0개");
    assertThat(command.body()).doesNotContain("미완료 할 일 0개");
    assertThat(command.payload())
        .containsEntry("calendarEventCount", 1L)
        .containsEntry("newsletterCount", 0L)
        .containsEntry("incompleteChecklistCount", 0L)
        .containsEntry("rangeStart", "2026-06-01")
        .containsEntry("rangeEnd", "2026-06-07");
  }

  @Test
  void createWeeklySummariesCreatesReadableBodyWithOnlyNonZeroItems() {
    User user = activeUser(3L);
    when(userRepository.findAllByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));
    stubWeeklyCounts(user.getId(), 0L, 2L, 3L);

    notificationScheduler.createWeeklySummaries(RANGE_START, RANGE_END);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);
    verify(notificationService).createNotification(eq(user.getId()), commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();
    assertThat(command.body()).isEqualTo("이번 주 가정통신문 2개, 미완료 할 일 3개를 확인해보세요");
    assertThat(command.payload())
        .containsEntry("calendarEventCount", 0L)
        .containsEntry("newsletterCount", 2L)
        .containsEntry("incompleteChecklistCount", 3L);
  }

  private void stubWeeklyCounts(
      Long userId, long calendarEventCount, long newsletterCount, long incompleteCount) {
    when(calendarEventRepository.countByUserIdAndStartAtGreaterThanEqualAndStartAtLessThan(
            eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
        .thenReturn(calendarEventCount);
    when(newsletterRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
        .thenReturn(newsletterCount);
    when(checklistRepository.countByUserIdAndTypeAndCompletedFalse(userId, ChecklistType.CHECKLIST))
        .thenReturn(incompleteCount);
  }

  private User activeUser(Long id) {
    OffsetDateTime now = OffsetDateTime.now(CLOCK);
    User user =
        User.builder()
            .email("weekly" + id + "@example.com")
            .loginId("weekly" + id)
            .passwordHash("encoded")
            .name("주간요약사용자" + id)
            .phoneNumber("0100000" + id)
            .status(UserStatus.ACTIVE)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
