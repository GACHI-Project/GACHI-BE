package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleCompleteRequest(@NotBlank String idToken, Boolean rememberMe) {}
