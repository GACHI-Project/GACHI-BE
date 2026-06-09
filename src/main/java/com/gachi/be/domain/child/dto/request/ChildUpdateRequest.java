package com.gachi.be.domain.child.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChildUpdateRequest(
    @Schema(description = "자녀 이름", example = "김민수")
        @Size(max = 50)
        @Pattern(regexp = ".*\\S.*", message = "name은 공백만 입력할 수 없습니다.")
        String name,
    @Schema(description = "학교명", example = "화랑초등학교")
        @Size(max = 120)
        @Pattern(regexp = ".*\\S.*", message = "schoolName은 공백만 입력할 수 없습니다.")
        String schoolName,
    @Schema(description = "NEIS 학교 코드", example = "7051173")
        @Size(max = 64)
        @Pattern(regexp = ".*\\S.*", message = "schoolCode는 공백만 입력할 수 없습니다.")
        String schoolCode,
    @Schema(description = "NEIS 시도교육청 코드", example = "B10")
        @Size(max = 20)
        @Pattern(regexp = ".*\\S.*", message = "officeCode는 공백만 입력할 수 없습니다.")
        String officeCode,
    @Schema(description = "학년", example = "4") @Min(1) @Max(6) Integer grade,
    @Schema(description = "반. NEIS CLASS_NM 값으로 사용한다.", example = "1")
        @Size(max = 20)
        @Pattern(regexp = ".*\\S.*", message = "className은 공백만 입력할 수 없습니다.")
        String className,
    @Schema(description = "캘린더 표시 색상", example = "#FF5A5A")
        @Pattern(regexp = "^#[A-Fa-f0-9]{6}$", message = "colorCode는 #RRGGBB 형식만 허용됩니다.")
        String colorCode) {}
