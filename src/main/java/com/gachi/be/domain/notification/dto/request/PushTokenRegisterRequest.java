package com.gachi.be.domain.notification.dto.request;

import com.gachi.be.domain.notification.entity.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushTokenRegisterRequest(
    @NotNull PushPlatform platform,
    @NotBlank @Size(max = 512) String token,
    @Size(max = 128) String deviceId,
    @Size(max = 50) String appVersion) {}
