package com.gachi.be.domain.checklist.dto.response;

import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.global.util.I18nTextResolver;

/** 오늘 마감 미완료 체크리스트 */
public record ChecklistTodayItem(
    Long checklistId, String content, String detail, String newsletterTitle, String childName) {

  public static ChecklistTodayItem of(
      Checklist checklist, String newsletterTitle, String childName, String language) {

    String content =
        I18nTextResolver.resolve(checklist.getContentI18n(), language, checklist.getContent());
    String detail =
        I18nTextResolver.resolve(checklist.getDetailI18n(), language, checklist.getDetail());

    return new ChecklistTodayItem(checklist.getId(), content, detail, newsletterTitle, childName);
  }
}
