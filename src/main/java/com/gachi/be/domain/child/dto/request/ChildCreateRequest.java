package com.gachi.be.domain.child.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChildCreateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 120) String schoolName,
    @Size(max = 64) String schoolCode,
    @NotNull @Min(1) @Max(6) Integer grade,
    @NotBlank
        @Pattern(
            regexp = "^#[A-Fa-f0-9]{6}$",
            message = "colorCode는 #RRGGBB 형식(예: #FF5A5A)만 허용합니다.")
        String colorCode) {}
