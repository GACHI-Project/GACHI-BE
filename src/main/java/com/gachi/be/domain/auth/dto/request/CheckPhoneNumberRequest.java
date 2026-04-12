package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckPhoneNumberRequest(
    @NotBlank
        @Pattern(regexp = PhoneNumberValidation.REGEXP, message = PhoneNumberValidation.MESSAGE)
        String phoneNumber) {}
