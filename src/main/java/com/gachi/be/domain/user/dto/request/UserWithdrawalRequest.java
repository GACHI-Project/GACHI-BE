package com.gachi.be.domain.user.dto.request;

import jakarta.validation.constraints.Size;

public record UserWithdrawalRequest(@Size(max = 100) String currentPassword) {}
