package com.gachi.be.domain.auth.dto.request;

import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record KakaoSignupRequest(
    @NotBlank String signupToken,
    @NotNull Boolean consentAgreed,
    @NotNull @Pattern(regexp = "^(KO|US|ZH|VI)$", message = "지원하지 않는 언어 코드입니다.")
        String languageCode,
    NotificationPreference notificationPreference,
    Boolean rememberMe) {}
