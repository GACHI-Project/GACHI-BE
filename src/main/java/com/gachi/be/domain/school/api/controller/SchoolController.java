package com.gachi.be.domain.school.api.controller;

import com.gachi.be.domain.school.dto.response.SchoolSearchResponse;
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
}
