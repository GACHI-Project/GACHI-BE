package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.Newsletter;

/**번역 결과 조회 API 응답 DTO*/
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsletterTranslationResponse(
    String originalText,
    String translatedText,
    String language) {

    public static NewsletterTranslationResponse from(Newsletter newsletter) {
        return new NewsletterTranslationResponse(
            newsletter.getOriginalText(),
            newsletter.getTranslatedText(),
            newsletter.getLanguage());
    }
}
