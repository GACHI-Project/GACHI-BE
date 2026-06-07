package com.gachi.be.domain.user.dto.request;

import com.gachi.be.domain.auth.dto.request.PhoneNumberValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank
        @Pattern(regexp = PhoneNumberValidation.REGEXP, message = PhoneNumberValidation.MESSAGE)
        String phoneNumber) {}
