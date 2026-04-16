package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String loginId, @NotBlank String password, Boolean rememberMe) {}
