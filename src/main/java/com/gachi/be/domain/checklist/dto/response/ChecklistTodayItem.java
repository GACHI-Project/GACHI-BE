package com.gachi.be.domain.checklist.dto.response;

import com.gachi.be.domain.checklist.entity.Checklist;

/** 오늘 마감 미완료 체크리스트 */
public record ChecklistTodayItem(
    Long checklistId, String content, String detail, String newsletterTitle, String childName) {

  public static ChecklistTodayItem of(
      Checklist checklist, String newsletterTitle, String childName) {
    return new ChecklistTodayItem(
        checklist.getId(),
        checklist.getContent(),
        checklist.getDetail(),
        newsletterTitle,
        childName);
  }
}
