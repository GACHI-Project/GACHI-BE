package com.gachi.be.domain.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.calendar.repository.CalendarEventRepository;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 저장된 알림 템플릿과 payload를 현재 사용자 언어의 title/body로 변환합니다. */
@Component
@RequiredArgsConstructor
public class NotificationTemplateRenderer {
  private static final String DEFAULT_LANGUAGE = "KO";
  private static final List<String> SUPPORTED_LANGUAGES = List.of("KO", "US", "ZH", "VI");
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final NewsletterRepository newsletterRepository;
  private final CalendarEventRepository calendarEventRepository;
  private final ChecklistRepository checklistRepository;
  private final ObjectMapper objectMapper;

  public RenderedNotification render(Notification notification, String languageCode) {
    String language = normalizeLanguage(languageCode);
    Map<String, Object> payload = readMap(notification.getPayloadJson());
    Map<String, Object> params = readMap(notification.getTemplateParamsJson());
    NotificationTemplateKey key = resolveTemplateKey(notification);

    if (key == null) {
      return fallback(notification);
    }

    return switch (key) {
      case NEWSLETTER_ANALYSIS -> renderNewsletterAnalysis(notification, language, payload, params);
      case DEADLINE_REMINDER -> renderDeadlineReminder(notification, language, payload, params);
      case CHECKLIST_DUE -> renderChecklistDue(notification, language, payload, params);
      case WEEKLY_SUMMARY -> renderWeeklySummary(language, payload);
    };
  }

  private RenderedNotification renderNewsletterAnalysis(
      Notification notification,
      String language,
      Map<String, Object> payload,
      Map<String, Object> params) {
    String newsletterTitle =
        firstText(
            i18nParam(params, "newsletterTitleI18n", language),
            textParam(params, "newsletterTitle"),
            newsletterTitleFromPayload(payload, language));
    if (!StringUtils.hasText(newsletterTitle)) {
      return fallback(notification);
    }
    return new RenderedNotification(
        switchLanguage(
            language,
            "새 가정통신문 분석 완료",
            "New newsletter analysis complete",
            "新家庭通知分析完成",
            "Đã hoàn tất phân tích thông báo mới"),
        switchLanguage(
            language,
            newsletterTitle + " 분석이 완료되었어요",
            newsletterTitle + " analysis is complete",
            newsletterTitle + "分析已完成",
            "Đã hoàn tất phân tích " + newsletterTitle));
  }

  private RenderedNotification renderDeadlineReminder(
      Notification notification,
      String language,
      Map<String, Object> payload,
      Map<String, Object> params) {
    String eventTitle =
        firstText(
            i18nParam(params, "eventTitleI18n", language),
            textParam(params, "eventTitle"),
            calendarEventTitleFromPayload(payload, language));
    if (!StringUtils.hasText(eventTitle)) {
      return fallback(notification);
    }
    return new RenderedNotification(
        switchLanguage(
            language,
            eventTitle + " 마감 D-1",
            eventTitle + " deadline D-1",
            eventTitle + " 截止 D-1",
            eventTitle + " hạn chót D-1"),
        switchLanguage(language, "내일 마감이에요", "Due tomorrow", "明天截止", "Hạn chót là ngày mai"));
  }

  private RenderedNotification renderChecklistDue(
      Notification notification,
      String language,
      Map<String, Object> payload,
      Map<String, Object> params) {
    String checklistContent =
        firstText(
            i18nParam(params, "checklistContentI18n", language),
            textParam(params, "checklistContent"),
            checklistContentFromPayload(payload, language));
    if (!StringUtils.hasText(checklistContent)) {
      return fallback(notification);
    }
    return new RenderedNotification(
        switchLanguage(
            language,
            "미완료 할 일이 있어요",
            "You have an incomplete task",
            "你有未完成的事项",
            "Bạn có việc chưa hoàn thành"),
        checklistContent);
  }

  private RenderedNotification renderWeeklySummary(String language, Map<String, Object> payload) {
    long calendarEventCount = longValue(payload.get("calendarEventCount"));
    long newsletterCount = longValue(payload.get("newsletterCount"));
    long incompleteChecklistCount = longValue(payload.get("incompleteChecklistCount"));
    return new RenderedNotification(
        switchLanguage(
            language,
            "이번 주 요약이 도착했어요",
            "Your weekly summary is here",
            "本周摘要已送达",
            "Tóm tắt tuần này đã sẵn sàng"),
        weeklyBody(language, calendarEventCount, newsletterCount, incompleteChecklistCount));
  }

  private String weeklyBody(
      String language, long calendarEventCount, long newsletterCount, long incompleteCount) {
    return switch (language) {
      case "US" -> {
        String items =
            joinNonEmpty(
                countText(calendarEventCount, "schedule " + calendarEventCount),
                countText(newsletterCount, "newsletter " + newsletterCount),
                countText(incompleteCount, "incomplete task " + incompleteCount));
        yield "Please check this week's " + items;
      }
      case "ZH" -> {
        String items =
            joinNonEmpty(
                countText(calendarEventCount, "日程 " + calendarEventCount + " 个"),
                countText(newsletterCount, "家庭通知 " + newsletterCount + " 个"),
                countText(incompleteCount, "未完成事项 " + incompleteCount + " 个"));
        yield "请查看本周的" + items;
      }
      case "VI" -> {
        String items =
            joinNonEmpty(
                countText(calendarEventCount, calendarEventCount + " lịch trình"),
                countText(newsletterCount, newsletterCount + " thông báo gia đình"),
                countText(incompleteCount, incompleteCount + " việc chưa hoàn thành"));
        yield "Vui lòng kiểm tra " + items + " trong tuần này";
      }
      default -> {
        String items =
            joinNonEmpty(
                countText(calendarEventCount, "일정 " + calendarEventCount + "개"),
                countText(newsletterCount, "가정통신문 " + newsletterCount + "개"),
                countText(incompleteCount, "미완료 할 일 " + incompleteCount + "개"));
        yield "이번 주 " + items + "를 확인해보세요";
      }
    };
  }

  private String joinNonEmpty(String... values) {
    return String.join(", ", java.util.Arrays.stream(values).filter(StringUtils::hasText).toList());
  }

  private String countText(long count, String text) {
    return count > 0 ? text : null;
  }

  private NotificationTemplateKey resolveTemplateKey(Notification notification) {
    if (StringUtils.hasText(notification.getTemplateKey())) {
      try {
        return NotificationTemplateKey.valueOf(notification.getTemplateKey());
      } catch (IllegalArgumentException ignored) {
        // 저장값이 손상되어도 type 기반 추론으로 기존 알림을 최대한 렌더링합니다.
      }
    }
    if (notification.getType() == NotificationType.NEWSLETTER_ANALYSIS) {
      return NotificationTemplateKey.NEWSLETTER_ANALYSIS;
    }
    if (notification.getType() == NotificationType.DEADLINE_REMINDER) {
      return NotificationTemplateKey.DEADLINE_REMINDER;
    }
    if (notification.getType() == NotificationType.CHECKLIST_DUE) {
      return NotificationTemplateKey.CHECKLIST_DUE;
    }
    if (notification.getType() == NotificationType.WEEKLY_SUMMARY) {
      return NotificationTemplateKey.WEEKLY_SUMMARY;
    }
    return null;
  }

  private String newsletterTitleFromPayload(Map<String, Object> payload, String language) {
    Long newsletterId = longObject(payload.get("newsletterId"));
    if (newsletterId == null) {
      return null;
    }
    return newsletterRepository
        .findById(newsletterId)
        .map(newsletter -> i18nText(newsletter.getTitleI18n(), language, newsletter.getTitle()))
        .orElse(null);
  }

  private String calendarEventTitleFromPayload(Map<String, Object> payload, String language) {
    Long calendarEventId = longObject(payload.get("calendarEventId"));
    if (calendarEventId == null) {
      return null;
    }
    return calendarEventRepository
        .findById(calendarEventId)
        .map(event -> i18nText(event.getTitleI18n(), language, event.getTitle()))
        .orElse(null);
  }

  private String checklistContentFromPayload(Map<String, Object> payload, String language) {
    Long checklistId = longObject(payload.get("checklistId"));
    if (checklistId == null) {
      return null;
    }
    return checklistRepository
        .findById(checklistId)
        .map(checklist -> i18nText(checklist.getContentI18n(), language, checklist.getContent()))
        .orElse(null);
  }

  private String i18nParam(Map<String, Object> params, String key, String language) {
    Object value = params.get(key);
    if (value instanceof Map<?, ?> map) {
      return firstText(stringValue(map.get(language)), stringValue(map.get(DEFAULT_LANGUAGE)));
    }
    return null;
  }

  private String i18nText(Map<String, String> values, String language, String fallback) {
    if (values == null || values.isEmpty()) {
      return fallback;
    }
    return firstText(values.get(language), values.get(DEFAULT_LANGUAGE), fallback);
  }

  private String textParam(Map<String, Object> params, String key) {
    Object value = params.get(key);
    return value instanceof String text && StringUtils.hasText(text) ? text : null;
  }

  private String stringValue(Object value) {
    return value instanceof String text && StringUtils.hasText(text) ? text : null;
  }

  private String firstText(String... values) {
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }

  private RenderedNotification fallback(Notification notification) {
    return new RenderedNotification(notification.getTitle(), notification.getBody());
  }

  private Map<String, Object> readMap(String json) {
    if (!StringUtils.hasText(json)) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception e) {
      return Collections.emptyMap();
    }
  }

  private String normalizeLanguage(String languageCode) {
    String normalized = languageCode == null ? DEFAULT_LANGUAGE : languageCode.trim().toUpperCase();
    return SUPPORTED_LANGUAGES.contains(normalized) ? normalized : DEFAULT_LANGUAGE;
  }

  private String switchLanguage(String language, String ko, String us, String zh, String vi) {
    return switch (language) {
      case "US" -> us;
      case "ZH" -> zh;
      case "VI" -> vi;
      default -> ko;
    };
  }

  private long longValue(Object value) {
    Long parsed = longObject(value);
    return parsed != null ? parsed : 0L;
  }

  private Long longObject(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && StringUtils.hasText(text)) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
