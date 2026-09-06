package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record KakaoCompleteRequest(@NotBlank String ticket, Boolean rememberMe) {}
