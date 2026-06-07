package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
    @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9._-]{4,50}$", message = "loginId는 4~50자의 영문/숫자/._-만 허용합니다.")
        String loginId,
    @NotBlank @Email String email,
    @NotBlank @Size(max = 100) String password,
    @NotBlank String passwordConfirm) {}
