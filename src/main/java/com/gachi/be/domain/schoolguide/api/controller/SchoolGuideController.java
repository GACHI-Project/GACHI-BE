package com.gachi.be.domain.schoolguide.api.controller;

import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideCreateRequest;
import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideUpdateRequest;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideCategoryResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideDetailResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideListResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuidePopularResponse;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.domain.schoolguide.service.SchoolGuideService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SchoolGuide", description = "한국 학교 생활 안내서 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/school-guide")
public class SchoolGuideController {

    private final SchoolGuideService schoolGuideService;

    /** 카테고리별 FAQ 개수 조회 (메인 화면 진입 시). */
    @Operation(
        summary = "카테고리별 FAQ 개수 조회",
        description = "학교 생활 안내서 메인 화면 진입 시 12개 카테고리별 질문 개수를 반환합니다.")
    @GetMapping("/categories")
    public ApiResponse<SchoolGuideCategoryResponse> getCategoryCounts() {
        return ApiResponse.success(
            SuccessCode.SCHOOL_GUIDE_CATEGORY_SUCCESS, schoolGuideService.getCategoryCounts());
    }

    /** 주간 인기 질문 TOP 2 조회. */
    @Operation(
        summary = "주간 인기 질문 TOP 2",
        description = "이번 주 조회수 기준 상위 2개 질문을 반환합니다. 매주 월요일 00:00(KST) 초기화됩니다.")
    @GetMapping("/faqs/popular")
    public ApiResponse<SchoolGuidePopularResponse> getPopularFaqs() {
        return ApiResponse.success(
            SuccessCode.SCHOOL_GUIDE_POPULAR_SUCCESS, schoolGuideService.getPopularFaqs());
    }

    /** FAQ 목록 조회 (카테고리 필터 or 검색). */
    @Operation(
        summary = "FAQ 목록 조회",
        description =
            """
            카테고리 필터 또는 검색어로 FAQ 목록을 조회합니다.
            - category: 카테고리 필터 (카테고리 탭 클릭 시)
            - search: 질문 텍스트 검색 (검색 시)
            - 둘 다 없으면 전체 반환
            - 둘 다 전달하면 에러
            """)
    @GetMapping("/faqs")
    public ApiResponse<SchoolGuideListResponse> getFaqs(
        @Parameter(description = "카테고리 필터") @RequestParam(required = false)
        SchoolGuideCategory category,
        @Parameter(description = "질문 검색 키워드") @RequestParam(required = false) String search) {

        if (category != null && search != null && !search.isBlank()) {
            throw new com.gachi.be.global.exception.BusinessException(
                com.gachi.be.global.code.ErrorCode.SCHOOL_GUIDE_FILTER_CONFLICT);
        }

        return ApiResponse.success(
            SuccessCode.SCHOOL_GUIDE_LIST_SUCCESS, schoolGuideService.getFaqs(category, search));
    }

    /** FAQ 상세 조회 + weekly_view_count 증가. */
    @Operation(
        summary = "FAQ 상세 조회",
        description = "FAQ 상세 정보(질문 + 답변)를 반환합니다. 호출 시 주간 조회수가 1 증가합니다.")
    @GetMapping("/faqs/{faqId}")
    public ApiResponse<SchoolGuideDetailResponse> getFaqDetail(
        @Parameter(description = "FAQ ID", required = true) @PathVariable Long faqId) {
        return ApiResponse.success(
            SuccessCode.SCHOOL_GUIDE_DETAIL_SUCCESS, schoolGuideService.getFaqDetail(faqId));
    }

    /** FAQ 등록 (관리용). */
    @Operation(summary = "[개발자용] FAQ 등록", description = "새 FAQ를 등록합니다. (관리용)")
    @PostMapping("/faqs")
    public ApiResponse<Void> createFaq(@Valid @RequestBody SchoolGuideCreateRequest request) {
        schoolGuideService.createFaq(request);
        return ApiResponse.success(SuccessCode.SCHOOL_GUIDE_CREATE_SUCCESS, null);
    }

    /** FAQ 수정 (관리용). */
    @Operation(summary = "[개발자용] FAQ 수정", description = "기존 FAQ를 수정합니다. (관리용) 전달된 필드만 수정됩니다.")
    @PatchMapping("/faqs/{faqId}")
    public ApiResponse<Void> updateFaq(
        @Parameter(description = "FAQ ID", required = true) @PathVariable Long faqId,
        @RequestBody SchoolGuideUpdateRequest request) {
        schoolGuideService.updateFaq(faqId, request);
        return ApiResponse.success(SuccessCode.SCHOOL_GUIDE_UPDATE_SUCCESS, null);
    }

    /** FAQ 삭제 (관리용). */
    @Operation(summary = "[개발자용] FAQ 삭제", description = "FAQ를 삭제합니다. (관리용)")
    @DeleteMapping("/faqs/{faqId}")
    public ApiResponse<Void> deleteFaq(
        @Parameter(description = "FAQ ID", required = true) @PathVariable Long faqId) {
        schoolGuideService.deleteFaq(faqId);
        return ApiResponse.success(SuccessCode.SCHOOL_GUIDE_DELETE_SUCCESS, null);
    }
}
