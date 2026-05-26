package com.gachi.be.domain.notification.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationReadRequest(
    @NotEmpty @Size(max = 100) List<@NotNull Long> notificationIds) {}
