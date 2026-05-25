package com.gachi.be.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;

/**알림 설정 변경 요청 DTO.*/
public record ChangeNotificationRequest(
    @NotNull(message = "notificationEnabled는 필수입니다.")
    boolean notificationEnabled
) {}
