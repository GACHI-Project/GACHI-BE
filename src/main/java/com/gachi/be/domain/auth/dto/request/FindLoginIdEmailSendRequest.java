package com.gachi.be.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FindLoginIdEmailSendRequest(@NotBlank @Email String email) {}
