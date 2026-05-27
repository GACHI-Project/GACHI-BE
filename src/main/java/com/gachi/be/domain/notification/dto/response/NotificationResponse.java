package com.gachi.be.domain.notification.dto.response;

import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import java.time.OffsetDateTime;
import java.util.Map;

public record NotificationResponse(
    Long id,
    NotificationType type,
    NotificationLevel level,
    Long childId,
    String childName,
    String title,
    String body,
    Map<String, Object> payload,
    boolean read,
    OffsetDateTime readAt,
    OffsetDateTime createdAt) {}
