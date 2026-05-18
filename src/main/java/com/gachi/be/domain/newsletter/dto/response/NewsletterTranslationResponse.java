package com.gachi.be.domain.newsletter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterDateCandidate;
import java.util.List;

/** 번역 결과 조회 API 응답 DTO */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NewsletterTranslationResponse(
    String originalText,
    String translatedText,
    String language,
    String fileUrl,
    List<NewsletterDateCandidateResponse> dateCandidates) {

  public static NewsletterTranslationResponse from(
      Newsletter newsletter, List<NewsletterDateCandidate> dateCandidates, String fileUrl) {
    return new NewsletterTranslationResponse(
        newsletter.getOriginalText(),
        newsletter.getTranslatedText(),
        newsletter.getLanguage(),
        fileUrl,
        dateCandidates == null
            ? List.of()
            : dateCandidates.stream().map(NewsletterDateCandidateResponse::from).toList());
  }
}
