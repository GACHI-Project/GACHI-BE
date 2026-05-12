package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.enums.DateCandidateExtractionType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** OCR 정제 이후 텍스트에서 명시적 단일 날짜 후보만 추출하고 표준 날짜로 정규화합니다. */
@Component
public class NewsletterDateCandidateExtractor {

  private static final Pattern FULL_DATE_PATTERN =
      Pattern.compile(
          "(?<year>(?:19|20)\\d{2})\\s*(?:\\uB144|[./-])\\s*"
              + "(?<month>1[0-2]|0?[1-9])\\s*(?:\\uC6D4|[./-])\\s*"
              + "(?<day>[12]\\d|3[01]|0?[1-9])\\s*\\uC77C?(?!\\d)");
  private static final Pattern KOREAN_MONTH_DAY_PATTERN =
      Pattern.compile(
          "(?<!\\d)(?<month>1[0-2]|0?[1-9])\\s*\\uC6D4\\s*"
              + "(?<day>[12]\\d|3[01]|0?[1-9])\\s*\\uC77C(?!\\d)");
  private static final Pattern SLASH_MONTH_DAY_PATTERN =
      Pattern.compile("(?<!\\d)(?<month>1[0-2]|0?[1-9])/(?<day>[12]\\d|3[01]|0?[1-9])(?![0-9/])");

  /**
   * 날짜 후보를 추출합니다.
   *
   * @param text OCR/번역 파이프라인에서 정제된 가정통신문 텍스트
   * @param referenceDate 연도가 생략된 날짜에 사용할 기준일
   * @return 문서 등장 순서대로 정렬된 날짜 후보 목록
   */
  public List<ExtractedDateCandidate> extract(String text, LocalDate referenceDate) {
    if (text == null || text.isBlank()) {
      return List.of();
    }

    LocalDate safeReferenceDate = referenceDate != null ? referenceDate : LocalDate.now();
    List<ExtractedDateCandidate> candidates = new ArrayList<>();

    collectFullDateCandidates(text, candidates);
    collectYearlessDateCandidates(text, safeReferenceDate, KOREAN_MONTH_DAY_PATTERN, candidates);
    collectYearlessDateCandidates(text, safeReferenceDate, SLASH_MONTH_DAY_PATTERN, candidates);

    return candidates.stream()
        .sorted(Comparator.comparingInt(ExtractedDateCandidate::startOffset))
        .toList();
  }

  private void collectFullDateCandidates(String text, List<ExtractedDateCandidate> candidates) {
    Matcher matcher = FULL_DATE_PATTERN.matcher(text);
    while (matcher.find()) {
      LocalDate date =
          parseDate(matcher.group("year"), matcher.group("month"), matcher.group("day"));
      addCandidate(
          candidates,
          text,
          matcher.start(),
          matcher.end(),
          date,
          DateCandidateExtractionType.REGEX);
    }
  }

  private void collectYearlessDateCandidates(
      String text,
      LocalDate referenceDate,
      Pattern pattern,
      List<ExtractedDateCandidate> candidates) {
    Matcher matcher = pattern.matcher(text);
    while (matcher.find()) {
      LocalDate date =
          parseDate(
              String.valueOf(referenceDate.getYear()),
              matcher.group("month"),
              matcher.group("day"));
      addCandidate(
          candidates,
          text,
          matcher.start(),
          matcher.end(),
          date,
          DateCandidateExtractionType.REGEX);
    }
  }

  private LocalDate parseDate(String year, String month, String day) {
    try {
      return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    } catch (DateTimeException | NumberFormatException e) {
      return null;
    }
  }

  private void addCandidate(
      List<ExtractedDateCandidate> candidates,
      String text,
      int startOffset,
      int endOffset,
      LocalDate normalizedDate,
      DateCandidateExtractionType extractionType) {
    if (normalizedDate == null || hasOverlap(candidates, startOffset, endOffset)) {
      return;
    }

    candidates.add(
        new ExtractedDateCandidate(
            text.substring(startOffset, endOffset),
            normalizedDate,
            startOffset,
            endOffset,
            extractionType));
  }

  private boolean hasOverlap(
      List<ExtractedDateCandidate> candidates, int startOffset, int endOffset) {
    return candidates.stream()
        .anyMatch(
            candidate ->
                startOffset < candidate.endOffset() && endOffset > candidate.startOffset());
  }

  public record ExtractedDateCandidate(
      String originalText,
      LocalDate normalizedDate,
      int startOffset,
      int endOffset,
      DateCandidateExtractionType extractionType) {}
}
