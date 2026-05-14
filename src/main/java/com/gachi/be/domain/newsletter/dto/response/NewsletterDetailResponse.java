package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 가정통신문 상세 조회 API 응답 DTO. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsletterDetailResponse(
    Long newsletterId,
    String title,
    String childName,
    String summary,
    String translatedText,
    String originalText,
    String language,
    boolean isCalendarRegistered,
    String createdAt) {
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public static NewsletterDetailResponse from(Newsletter newsletter, boolean calendarRegistered) {
    String createdAtStr =
        newsletter.getCreatedAt() == null
            ? null
            : newsletter
                .getCreatedAt()
                .atZoneSameInstant(KST)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    return new NewsletterDetailResponse(
        newsletter.getId(),
        newsletter.getTitle(),
        newsletter.getChildName(),
        newsletter.getSummary(),
        newsletter.getTranslatedText(),
        newsletter.getOriginalText(),
        newsletter.getLanguage(),
        calendarRegistered,
        createdAtStr);
  }
}
