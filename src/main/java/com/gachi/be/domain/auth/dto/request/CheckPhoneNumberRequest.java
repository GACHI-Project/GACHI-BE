package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckPhoneNumberRequest(
    @NotBlank
        @Pattern(
            regexp = "^[0-9]{3}-?[0-9]{3,4}-?[0-9]{4}$",
            message = "전화번호는 숫자만 입력하거나 하이픈(-)을 포함해 주세요.")
        String phoneNumber) {}
