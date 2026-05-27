package com.gachi.be.domain.user.dto.request;

import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import jakarta.validation.constraints.NotNull;

/** 설정 화면에서 알림 수신 단계를 변경할 때 사용하는 요청 DTO. */
public record ChangeNotificationRequest(
    @NotNull(message = "notificationPreference는 필수입니다.")
        NotificationPreference notificationPreference) {}
