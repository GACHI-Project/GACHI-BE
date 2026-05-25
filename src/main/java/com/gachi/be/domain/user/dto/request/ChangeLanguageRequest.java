package com.gachi.be.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 언어 설정 변경 요청 DTO. */
public record ChangeLanguageRequest(
    @NotBlank(message = "언어 코드는 필수입니다.")
        @Pattern(
            regexp = "^(KO|US|ZH|VI)$",
            message = "지원하지 않는 언어 코드입니다. KO, US, ZH, VI 중 하나여야 합니다.")
        String languageCode) {}
