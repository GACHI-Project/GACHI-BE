package com.gachi.be.domain.user.dto.response;

import java.time.LocalDateTime;

/** 내 정보 조회 응답 DTO. */
public record UserMeResponse(
    Long userId,
    String loginId,
    String email,
    String name,
    String languageCode,
    Boolean notificationEnabled,
    LocalDateTime createdAt) {}
