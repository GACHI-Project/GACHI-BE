package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.NewsletterDateCandidate;
import com.gachi.be.domain.newsletter.entity.enums.DateCandidateExtractionType;
import java.time.LocalDate;

public record NewsletterDateCandidateResponse(
    String originalText,
    LocalDate normalizedDate,
    int startOffset,
    int endOffset,
    DateCandidateExtractionType extractionType) {

  public static NewsletterDateCandidateResponse from(NewsletterDateCandidate candidate) {
    return new NewsletterDateCandidateResponse(
        candidate.originalText(),
        candidate.normalizedDate(),
        candidate.startOffset(),
        candidate.endOffset(),
        candidate.extractionType());
  }
}
