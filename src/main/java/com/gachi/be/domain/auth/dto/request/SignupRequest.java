package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Email String email,
    @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "loginId는 4~50자 영문/숫자/._-만 허용됩니다.")
        String loginId,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank String passwordConfirm,
    @NotBlank @Pattern(regexp = "^[0-9]{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.")
        String phoneNumber,
    @NotNull Boolean consentAgreed) {}
