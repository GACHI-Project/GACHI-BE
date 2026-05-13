package com.gachi.be.domain.newsletter.entity;

import com.gachi.be.domain.newsletter.entity.enums.DateCandidateExtractionType;
import java.time.LocalDate;

/** 가정통신문 원문에서 발견한 날짜 후보 값입니다. */
public record NewsletterDateCandidate(
    String originalText,
    LocalDate normalizedDate,
    int startOffset,
    int endOffset,
    DateCandidateExtractionType extractionType) {}
