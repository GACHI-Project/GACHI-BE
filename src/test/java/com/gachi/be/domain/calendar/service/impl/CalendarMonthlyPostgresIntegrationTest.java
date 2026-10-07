package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gachi.be.domain.calendar.dto.response.CalendarMonthlyResponse.MarkerType;
import com.gachi.be.domain.calendar.entity.CalendarEvent;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CalendarMonthlyPostgresIntegrationTest {
  @Container
  static final GenericContainer<?> POSTGRES =
      new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withEnv("POSTGRES_DB", "gachi_test")
          .withEnv("POSTGRES_USER", "gachi")
          .withEnv("POSTGRES_PASSWORD", "gachidbpw")
          .withExposedPorts(5432);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(5432)
                + "/gachi_test");
    registry.add("spring.datasource.username", () -> "gachi");
    registry.add("spring.datasource.password", () -> "gachidbpw");
  }

  @Autowired private CalendarEventRepository calendarEventRepository;

  @Test
  void monthlyFindsPeriodStartAndDeadlineInTheirRespectiveMonths() {
    CalendarEvent event =
        calendarEventRepository.saveAndFlush(
            CalendarEvent.builder()
                .userId(7L)
                .newsletterId(10L)
                .childName("민수")
                .childColor("#12AB34")
                .title("신청 마감")
                .startAt(OffsetDateTime.parse("2026-09-28T18:00:00+09:00"))
                .periodStartAt("2026-08-31T10:00:00+09:00")
                .build());
    CalendarQueryServiceImpl service =
        new CalendarQueryServiceImpl(
            calendarEventRepository,
            mock(ChecklistRepository.class),
            mock(NewsletterRepository.class),
            mock(UserRepository.class));

    var august = service.getMonthly(7L, 2026, 8, "민수").markedDates();
    var september = service.getMonthly(7L, 2026, 9, "민수").markedDates();

    assertThat(august).hasSize(1);
    assertThat(august.get(0).eventId()).isEqualTo(event.getId());
    assertThat(august.get(0).date()).isEqualTo("2026-08-31");
    assertThat(august.get(0).markerTypes()).containsExactly(MarkerType.START);
    assertThat(september).hasSize(1);
    assertThat(september.get(0).eventId()).isEqualTo(event.getId());
    assertThat(september.get(0).date()).isEqualTo("2026-09-28");
    assertThat(september.get(0).markerTypes()).containsExactly(MarkerType.DEADLINE);
  }

  @Test
  void monthlyFindsDateOnlyScheduleEndAcrossUtcMonthBoundary() {
    CalendarEvent event =
        calendarEventRepository.saveAndFlush(
            CalendarEvent.builder()
                .userId(7L)
                .newsletterId(11L)
                .childName("민수")
                .childColor("#12AB34")
                .title("운영 기간")
                .startAt(OffsetDateTime.parse("2026-10-31T00:00:00+09:00"))
                .endAt(OffsetDateTime.parse("2026-10-31T15:00:00Z"))
                .allDay(true)
                .endAllDay(true)
                .build());
    CalendarQueryServiceImpl service =
        new CalendarQueryServiceImpl(
            calendarEventRepository,
            mock(ChecklistRepository.class),
            mock(NewsletterRepository.class),
            mock(UserRepository.class));

    var october = service.getMonthly(7L, 2026, 10, "민수").markedDates();
    var november = service.getMonthly(7L, 2026, 11, "민수").markedDates();

    assertThat(october).hasSize(1);
    assertThat(october.get(0).eventId()).isEqualTo(event.getId());
    assertThat(october.get(0).date()).isEqualTo("2026-10-31");
    assertThat(october.get(0).markerTypes()).containsExactly(MarkerType.START);
    assertThat(november).hasSize(1);
    assertThat(november.get(0).date()).isEqualTo("2026-11-01");
    assertThat(november.get(0).markerTypes()).containsExactly(MarkerType.END);
  }
}
