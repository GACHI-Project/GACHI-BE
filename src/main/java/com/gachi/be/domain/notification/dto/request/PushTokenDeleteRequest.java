package com.gachi.be.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenDeleteRequest(@NotBlank @Size(max = 512) String token) {}
