package com.gachi.be.domain.checklist.service;

import com.gachi.be.domain.checklist.dto.request.ChecklistCompleteRequest;
import com.gachi.be.domain.checklist.dto.response.ChecklistTodayResponse;

public interface ChecklistService {

    /** 오늘 마감 미완료 체크리스트 조회 (홈 화면) */
    ChecklistTodayResponse getTodayChecklists(Long userId);

    /** 완료 토글 */
    void updateComplete(Long userId, Long checklistId, ChecklistCompleteRequest request);

    /** 체크리스트 삭제 */
    void deleteChecklist(Long userId, Long checklistId);
}
