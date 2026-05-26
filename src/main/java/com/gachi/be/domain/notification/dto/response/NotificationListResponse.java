package com.gachi.be.domain.notification.dto.response;

import java.util.List;

public record NotificationListResponse(
    List<NotificationResponse> notifications, Long nextCursor, boolean hasNext) {}
