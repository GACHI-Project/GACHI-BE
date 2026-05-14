package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.Newsletter;

/** 요약 조회 API 응답 DTO. -> isCalendarRegister로 저장 여부 파악*/
public record NewsletterSummaryResponse(
    String title,
    String summary) {
    public static NewsletterSummaryResponse from(Newsletter newsletter) {
        return new NewsletterSummaryResponse(newsletter.getTitle(), newsletter.getSummary());
    }
}
