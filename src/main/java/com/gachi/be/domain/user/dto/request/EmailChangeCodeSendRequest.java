package com.gachi.be.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeCodeSendRequest(
    @NotBlank @Email String email, @NotBlank String currentPassword) {}
