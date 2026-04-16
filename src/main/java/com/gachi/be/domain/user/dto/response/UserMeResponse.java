package com.gachi.be.domain.user.dto.response;

/** 내 정보 조회 응답 DTO. */
public record UserMeResponse(
    Long userId, String loginId, String email, String name, String phoneNumber) {}
