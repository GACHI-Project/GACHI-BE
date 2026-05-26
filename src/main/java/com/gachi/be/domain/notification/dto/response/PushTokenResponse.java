package com.gachi.be.domain.notification.dto.response;

import com.gachi.be.domain.notification.entity.enums.PushPlatform;
import java.time.OffsetDateTime;

public record PushTokenResponse(
    Long id,
    PushPlatform platform,
    String deviceId,
    String appVersion,
    boolean enabled,
    OffsetDateTime lastRegisteredAt) {}
