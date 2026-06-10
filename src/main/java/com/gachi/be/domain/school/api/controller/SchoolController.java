package com.gachi.be.domain.school.api.controller;

import com.gachi.be.domain.school.dto.response.SchoolClassResponse;
import com.gachi.be.domain.school.dto.response.SchoolSearchResponse;
import com.gachi.be.domain.school.service.SchoolClassService;
import com.gachi.be.domain.school.service.SchoolSearchService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "School", description = "학교 검색 API")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/schools")
public class SchoolController {
  private final SchoolSearchService schoolSearchService;
  private final SchoolClassService schoolClassService;

  @Operation(summary = "학교명 검색", description = "NEIS 학교기본정보를 이용해 학교명을 검색합니다.")
  @GetMapping("/search")
  public ApiResponse<SchoolSearchResponse> searchSchools(
      @Parameter(description = "검색할 학교명 키워드") @RequestParam @NotBlank @Size(min = 2, max = 50)
          String keyword,
      @Parameter(description = "검색 결과 개수. 기본 10, 최대 20")
          @RequestParam(defaultValue = "10")
          @Min(1)
          @Max(20)
          Integer size) {
    return ApiResponse.success(
        SuccessCode.SCHOOL_SEARCH_SUCCESS, schoolSearchService.search(keyword, size));
  }

  @Operation(summary = "학교 반 목록 조회", description = "NEIS 학급정보를 이용해 선택한 학교와 학년의 반 목록을 조회합니다.")
  @GetMapping("/classes")
  public ApiResponse<SchoolClassResponse> searchSchoolClasses(
      @Parameter(description = "시도교육청코드", required = true) @RequestParam @NotBlank
          String officeCode,
      @Parameter(description = "학교 행정표준코드", required = true) @RequestParam @NotBlank
          String schoolCode,
      @Parameter(description = "학년도(YYYY). 미입력 시 현재 학년도") @RequestParam(required = false)
          String academicYear,
      @Parameter(description = "초등학교 학년(1~6)") @RequestParam(required = false) @Min(1) @Max(6)
          Integer grade) {
    return ApiResponse.success(
        SuccessCode.SCHOOL_CLASS_LIST_SUCCESS,
        schoolClassService.search(officeCode, schoolCode, academicYear, grade));
  }
}
