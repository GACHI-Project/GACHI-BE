package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordResetEmailVerifyRequest(
    @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "loginId는 4~50자의 영문/숫자/._-만 허용합니다.")
        String loginId,
    @NotBlank @Email String email,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "인증 코드는 6자리 숫자여야 합니다.") String code) {}
