package com.gachi.be.domain.auth.dto.response;

public record SignupResponse(
    Long userId, String loginId, String email, String name, String phoneNumber) {}
