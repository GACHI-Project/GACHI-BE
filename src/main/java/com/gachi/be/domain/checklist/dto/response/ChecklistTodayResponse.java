package com.gachi.be.domain.checklist.dto.response;

import java.util.List;

/**홈 화면 상단에 표시되는 오늘 마감 미완료 체크리스트 목록.*/
public record ChecklistTodayResponse(List<ChecklistTodayItem> checklists) {

    public static ChecklistTodayResponse of(List<ChecklistTodayItem> checklists) {
        return new ChecklistTodayResponse(checklists);
    }
}
