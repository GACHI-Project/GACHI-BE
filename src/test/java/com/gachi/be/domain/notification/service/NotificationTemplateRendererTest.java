package com.gachi.be.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateRendererTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final NotificationTemplateRenderer renderer =
      new NotificationTemplateRenderer(
          mock(NewsletterRepository.class),
          mock(CalendarEventRepository.class),
          mock(ChecklistRepository.class),
          objectMapper);

  @Test
  void renderUsesCurrentLanguageTemplateAndI18nParams() throws Exception {
    Notification notification =
        Notification.builder()
            .userId(1L)
            .type(NotificationType.NEWSLETTER_ANALYSIS)
            .title("새 가정통신문 분석 완료")
            .body("토요 베이커리 안내 분석이 완료되었어요")
            .payloadJson(objectMapper.writeValueAsString(Map.of("newsletterId", 10L)))
            .templateKey(NotificationTemplateKey.NEWSLETTER_ANALYSIS.name())
            .templateParamsJson(
                objectMapper.writeValueAsString(
                    Map.of(
                        "newsletterTitle",
                        "토요 베이커리 안내",
                        "newsletterTitleI18n",
                        Map.of(
                            "KO",
                            "토요 베이커리 안내",
                            "US",
                            "Saturday Bakery Guide",
                            "ZH",
                            "周六烘焙通知",
                            "VI",
                            "Thông báo làm bánh thứ Bảy"))))
            .dedupeKey("newsletter-analysis:10")
            .build();

    RenderedNotification rendered = renderer.render(notification, "US");

    assertThat(rendered.title()).isEqualTo("New newsletter analysis complete");
    assertThat(rendered.body()).isEqualTo("Saturday Bakery Guide analysis is complete");
  }

  @Test
  void renderFallsBackToStoredTextWhenTemplateCannotBeResolved() {
    Notification notification =
        Notification.builder()
            .userId(1L)
            .type(NotificationType.SYSTEM)
            .title("system title")
            .body("system body")
            .dedupeKey("system:1")
            .build();

    RenderedNotification rendered = renderer.render(notification, "US");

    assertThat(rendered.title()).isEqualTo("system title");
    assertThat(rendered.body()).isEqualTo("system body");
  }

  @Test
  void renderFallsBackToTypeWhenTemplateKeyIsInvalid() throws Exception {
    Notification notification =
        Notification.builder()
            .userId(1L)
            .type(NotificationType.CHECKLIST_DUE)
            .title("미완료 할 일이 있어요")
            .body("준비물 챙기기")
            .templateKey("BROKEN_KEY")
            .templateParamsJson(
                objectMapper.writeValueAsString(
                    Map.of(
                        "checklistContent",
                        "준비물 챙기기",
                        "checklistContentI18n",
                        Map.of("US", "Pack supplies"))))
            .dedupeKey("checklist:1")
            .build();

    RenderedNotification rendered = renderer.render(notification, "US");

    assertThat(rendered.title()).isEqualTo("You have an incomplete task");
    assertThat(rendered.body()).isEqualTo("Pack supplies");
  }
}
