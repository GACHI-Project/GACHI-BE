package com.gachi.be.domain.checklist.api.controller;

import com.gachi.be.domain.checklist.dto.request.ChecklistCompleteRequest;
import com.gachi.be.domain.checklist.dto.response.ChecklistTodayResponse;
import com.gachi.be.domain.checklist.service.ChecklistService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Checklist", description = "체크리스트 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/checklists")
public class ChecklistController {

    private final ChecklistService checklistService;

    /** 오늘 마감 미완료 체크리스트 조회 API. */
    @Operation(
        summary = "오늘 마감 미완료 체크리스트 조회",
        description = """
          홈 화면 상단에 표시되는 오늘 마감 미완료 체크리스트 목록을 반환합니다.
          오늘 일정이 없거나 모두 완료된 경우 빈 리스트를 반환합니다.
          전체 자녀의 체크리스트를 조회합니다.
          """)
    @GetMapping("/today")
    public ApiResponse<ChecklistTodayResponse> getTodayChecklists(
        @AuthenticationPrincipal Long userId) {

        ChecklistTodayResponse response = checklistService.getTodayChecklists(userId);
        return ApiResponse.success(SuccessCode.CHECKLIST_TODAY_SUCCESS, response);
    }

    /** 체크리스트 완료 처리 API. */
    @Operation(
        summary = "체크리스트 완료 처리",
        description = """
          체크박스 클릭 시 완료 상태를 토글합니다.
          isCompleted: true → 완료, false → 미완료로 되돌리기.
          CHECKLIST, TODO 타입 모두 처리 가능합니다.
          """)
    @PatchMapping("/{checklistId}/complete")
    public ApiResponse<Void> updateComplete(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "체크리스트 ID", required = true) @PathVariable Long checklistId,
        @Valid @RequestBody ChecklistCompleteRequest request) {

        checklistService.updateComplete(userId, checklistId, request);
        return ApiResponse.success(SuccessCode.CHECKLIST_COMPLETE_SUCCESS, null);
    }

    /**체크리스트 단건 삭제 API. */
    @Operation(
        summary = "체크리스트 삭제",
        description = """
          체크리스트 항목을 삭제합니다.
          CHECKLIST 타입만 삭제 가능합니다.
          캘린더 등록 전/후 모두 삭제 가능합니다.
          """)
    @DeleteMapping("/{checklistId}")
    public ApiResponse<Void> deleteChecklist(
        @AuthenticationPrincipal Long userId,
        @Parameter(description = "삭제할 체크리스트 ID", required = true) @PathVariable Long checklistId) {

        checklistService.deleteChecklist(userId, checklistId);
        return ApiResponse.success(SuccessCode.CHECKLIST_DELETED, null);
    }
}
