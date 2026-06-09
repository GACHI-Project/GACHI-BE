package com.gachi.be.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
    @NotBlank @Size(max = 100) String currentPassword,
    @NotBlank @Size(max = 100) String newPassword,
    @NotBlank @Size(max = 100) String newPasswordConfirm) {}
