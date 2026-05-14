package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;

import java.util.List;

/** 체크리스트 탭에서 표시되는 항목 목록. */
public record NewsletterChecklistResponse(List<ChecklistItem> items) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ChecklistItem(
      Long checklistId,
      String type,
      String content,
      String detail,
      boolean isCompleted,
      String dueDate,
      String targetDate,
      String targetDateLabel) {

      public static ChecklistItem ofChecklist(Checklist checklist, String dueDateStr) {
          return new ChecklistItem(
              checklist.getId(),
              ChecklistType.CHECKLIST.name(),
              checklist.getContent(),
              checklist.getDetail(),    // CHECKLIST: 상세설명 포함
              checklist.isCompleted(),
              dueDateStr,               // CHECKLIST: 마감일 포함
              null,                     // TODO 전용 -> null
              null);                    // TODO 전용 -> null
      }


      public static ChecklistItem ofTodo(Checklist checklist) {
          String targetDateStr =
              checklist.getTargetDate() != null ? checklist.getTargetDate().toString() : null;

          return new ChecklistItem(
              checklist.getId(),
              ChecklistType.TODO.name(),
              checklist.getContent(),
              null,                           // CHECKLIST 전용 -> null
              checklist.isCompleted(),
              null,                           // CHECKLIST 전용 -> null
              targetDateStr,                  // TODO: 절대 날짜
              checklist.getTargetDateLabel()); // TODO: "지금 바로", "내일" 등
      }

      // 체크리스트 타입일 때만 사용
      @Deprecated
      public static ChecklistItem of(Checklist checklist, String startAtStr) {
          return ofChecklist(checklist, startAtStr);
      }
  }

  public static NewsletterChecklistResponse of(List<ChecklistItem> items) {
    return new NewsletterChecklistResponse(items);
  }
}
