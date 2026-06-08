package com.gachi.be.domain.child.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChildUpdateRequest(
    @Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "name은 공백만 입력할 수 없습니다.") String name,
    @Size(max = 120) @Pattern(regexp = ".*\\S.*", message = "schoolName은 공백만 입력할 수 없습니다.")
        String schoolName,
    @Size(max = 64) @Pattern(regexp = ".*\\S.*", message = "schoolCode는 공백만 입력할 수 없습니다.")
        String schoolCode,
    @Size(max = 20) @Pattern(regexp = ".*\\S.*", message = "officeCode는 공백만 입력할 수 없습니다.")
        String officeCode,
    @Min(1) @Max(6) Integer grade,
    @Size(max = 20) @Pattern(regexp = ".*\\S.*", message = "className은 공백만 입력할 수 없습니다.")
        String className,
    @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "colorCode는 #RRGGBB 형식(예: #FF5A5A)만 허용합니다.")
        String colorCode) {}
