package com.gachi.be.domain.checklist.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChecklistCompleteRequest(
    @NotNull(message = "isCompleted는 필수입니다.") Boolean isCompleted) {}
