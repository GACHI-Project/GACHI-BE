package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.notification.entity.enums.NotificationType;
import java.util.Map;

/** 다른 도메인에서 사용자 알림을 만들 때 넘기는 최소 입력값. */
public record NotificationCreateCommand(
    NotificationType type,
    String title,
    String body,
    Map<String, Object> payload,
    String dedupeKey) {}
