package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.checklist.entity.Checklist;
import java.util.List;

/** 체크리스트 탭에서 표시되는 항목 목록. */
public record NewsletterChecklistResponse(List<ChecklistItem> items) {

  public record ChecklistItem(
      Long checklistId,
      String type,
      String content,
      String detail,
      boolean isCompleted,

      /** 연결된 일정의 KST 날짜 (YYYY-MM-DD). 캘린더 등록 전이면 null */
      String dueDate) {
    public static ChecklistItem of(Checklist checklist, String startAtStr) {
      return new ChecklistItem(
          checklist.getId(),
          checklist.getType().name(),
          checklist.getContent(),
          checklist.getDetail(),
          checklist.isCompleted(),
          startAtStr);
    }
  }

  public static NewsletterChecklistResponse of(List<ChecklistItem> items) {
    return new NewsletterChecklistResponse(items);
  }
}
