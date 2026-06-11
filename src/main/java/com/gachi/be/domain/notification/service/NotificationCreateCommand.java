package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import java.util.Map;

/** 다른 도메인이 사용자 알림을 생성할 때 전달하는 최소 입력값. */
public record NotificationCreateCommand(
    NotificationType type,
    String title,
    String body,
    Map<String, Object> payload,
    NotificationTemplateKey templateKey,
    Map<String, Object> templateParams,
    String dedupeKey,
    NotificationLevel level,
    Long childId,
    String childName) {

  public NotificationCreateCommand(
      NotificationType type,
      String title,
      String body,
      Map<String, Object> payload,
      String dedupeKey) {
    this(
        type, title, body, payload, null, null, dedupeKey, NotificationLevel.IMPORTANT, null, null);
  }

  public NotificationCreateCommand(
      NotificationType type,
      String title,
      String body,
      Map<String, Object> payload,
      String dedupeKey,
      NotificationLevel level,
      Long childId,
      String childName) {
    this(type, title, body, payload, null, null, dedupeKey, level, childId, childName);
  }
}
