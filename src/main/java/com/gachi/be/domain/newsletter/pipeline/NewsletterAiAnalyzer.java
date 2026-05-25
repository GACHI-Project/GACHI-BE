package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.ExtractedItem;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsletterAiAnalyzer {

  private static final String DEFAULT_TITLE = "가정통신문 안내";
  private static final int TITLE_MAX_LENGTH = 80;
  private static final int SUMMARY_MAX_LENGTH = 300;
  private static final int CHECKLIST_TEXT_MAX_LENGTH = 500;

  private final AiNewsletterClient aiNewsletterClient;
  private final ChecklistRepository checklistRepository;
  private final CalendarPreviewRedisService calendarPreviewRedisService;
  private final NewsletterRepository newsletterRepository;
  private final PapagoTranslateClient papagoTranslateClient;

  public AiAnalysisResult analyze(
      Long newsletterId, String originalText, String translatedText, String language) {
    log.info("[AiAnalyzer] AI 서버 분석 시작. newsletterId={}, language={}", newsletterId, language);

    Newsletter newsletter =
        newsletterRepository
            .findById(newsletterId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "[AiAnalyzer] newsletter를 찾을 수 없습니다. newsletterId=" + newsletterId));

    AnalysisResponse analysisResponse =
        aiNewsletterClient.analyze(
            originalText, translatedText, language, newsletter.getDateCandidates());
    List<ExtractedItem> items = analysisResponse.items();

    List<SavedExtractedItem> savedItems =
        saveExtractedItems(newsletterId, newsletter.getUserId(), items, language);

    try {
      saveCalendarPreview(newsletterId, savedItems);
    } catch (RuntimeException e) {
      log.warn(
          "[AiAnalyzer] 캘린더 preview 저장 실패. 분석 결과 저장은 계속 진행합니다. newsletterId={}", newsletterId, e);
    }

    String rawTitle = normalizeTitle(analysisResponse.title(), originalText);
    String title = translateIfNeeded(rawTitle, language, "title", newsletterId);
    String summary = normalizeSummary(analysisResponse.summary(), translatedText, originalText);

    log.info(
        "[AiAnalyzer] AI 서버 분석 완료. newsletterId={}, extractedItems={}", newsletterId, items.size());
    return new AiAnalysisResult(title, summary);
  }

  private List<SavedExtractedItem> saveExtractedItems(
      Long newsletterId, Long userId, List<ExtractedItem> items, String language) {
    if (items == null || items.isEmpty()) {
      log.warn("[AiAnalyzer] AI 서버 항목 추출 결과 없음. newsletterId={}", newsletterId);
      return List.of();
    }

    List<ExtractedItem> validItems =
        items.stream().filter(item -> item.title() != null && !item.title().isBlank()).toList();
    List<Checklist> entities =
        validItems.stream().map(item -> toChecklist(newsletterId, userId, item, language)).toList();

    if (entities.isEmpty()) {
      log.warn("[AiAnalyzer] 저장 가능한 항목 없음. newsletterId={}", newsletterId);
      return List.of();
    }

    List<Checklist> savedEntities = checklistRepository.saveAll(entities);
    log.debug("[AiAnalyzer] AI 서버 추출 항목 {}개 저장 완료.", entities.size());

    List<SavedExtractedItem> savedItems = new ArrayList<>();
    for (int i = 0; i < validItems.size(); i++) {
      savedItems.add(new SavedExtractedItem(validItems.get(i), savedEntities.get(i)));
    }
    return savedItems;
  }

  private Checklist toChecklist(
      Long newsletterId, Long userId, ExtractedItem item, String language) {
    ChecklistType checklistType =
        "checklist".equalsIgnoreCase(item.type()) ? ChecklistType.CHECKLIST : ChecklistType.TODO;

    LocalDate targetDate = parseTargetDate(item.datetime());

    String targetDateLabel = null;
    if (targetDate != null) {
      String koreanLabel = targetDate.getMonthValue() + "월 " + targetDate.getDayOfMonth() + "일";
      targetDateLabel = translateIfNeeded(koreanLabel, language, "targetDateLabel", newsletterId);
    }

    // content: AI 서버가 한국어로 추출한 항목명을 파파고로 번역
    String content =
        translateIfNeeded(
            trimToMax(item.title().trim(), CHECKLIST_TEXT_MAX_LENGTH),
            language,
            "content",
            newsletterId);

    // detail: AI 서버가 한국어로 추출한 상세 설명을 파파고로 번역
    String detail = null;
    if (item.evidenceText() != null && !item.evidenceText().isBlank()) {
      String trimmedDetail = trimNullable(item.evidenceText(), CHECKLIST_TEXT_MAX_LENGTH);
      detail = translateIfNeeded(trimmedDetail, language, "detail", newsletterId);
    }

    return Checklist.builder()
        .newsletterId(newsletterId)
        .calendarEventId(null)
        .userId(userId)
        .type(checklistType)
        .content(content)
        .detail(detail)
        .targetDate(checklistType == ChecklistType.TODO ? targetDate : null)
        .targetDateLabel(checklistType == ChecklistType.TODO ? targetDateLabel : null)
        .build();
  }

  /** KO가 아닌 경우 파파고로 번역. KO이거나 번역 실패 시 원문 그대로 반환. */
  private String translateIfNeeded(
      String text, String language, String fieldName, Long newsletterId) {
    if (text == null || text.isBlank()) {
      return text;
    }
    if ("KO".equals(language)) {
      // KO는 번역 스킵, 원문 그대로 반환
      return text;
    }
    try {
      String translated = papagoTranslateClient.translate(text, language);
      if (translated != null && !translated.isBlank()) {
        log.debug(
            "[AiAnalyzer] {} 번역 완료. newsletterId={}, language={}",
            fieldName,
            newsletterId,
            language);
        return translated;
      }
    } catch (Exception e) {
      // 번역 실패 시 원문 유지 (파이프라인 전체를 중단하지 않음)
      log.warn(
          "[AiAnalyzer] {} 번역 실패. 원문 유지. newsletterId={}, language={}, error={}",
          fieldName,
          newsletterId,
          language,
          e.getMessage());
    }
    return text;
  }

  private LocalDate parseTargetDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
    } catch (DateTimeParseException e) {
      log.warn("[AiAnalyzer] AI 서버 날짜 파싱 실패. value={}", value);
      return null;
    }
  }

  private void saveCalendarPreview(Long newsletterId, List<SavedExtractedItem> savedItems) {
    List<CalendarPreviewEvent> previewEvents = new ArrayList<>();

    for (SavedExtractedItem savedItem : savedItems) {
      ExtractedItem item = savedItem.item();
      String extractedDate = normalizePreviewDate(item.datetime());
      if (!"confirmed".equalsIgnoreCase(item.dateStatus()) || extractedDate == null) {
        continue;
      }

      previewEvents.add(
          new CalendarPreviewEvent(
              "ai_evt_" + (previewEvents.size() + 1),
              trimToMax(item.title().trim(), CHECKLIST_TEXT_MAX_LENGTH),
              extractedDate,
              true,
              checklistIdList(savedItem.checklist())));
    }

    if (previewEvents.isEmpty()) {
      // 재분석 결과에 확정 날짜가 없으면 이전 미리보기 데이터가 남아 잘못 등록될 수 있어 비운다.
      calendarPreviewRedisService.deletePreview(newsletterId);
      log.debug("[AiAnalyzer] 캘린더 preview 생성 대상 없음. newsletterId={}", newsletterId);
      return;
    }

    calendarPreviewRedisService.savePreview(newsletterId, previewEvents);
    log.debug(
        "[AiAnalyzer] 캘린더 preview {}개 저장 완료. newsletterId={}", previewEvents.size(), newsletterId);
  }

  private String normalizePreviewDate(String value) {
    LocalDate targetDate = parseTargetDate(value);
    return targetDate != null ? targetDate.toString() : null;
  }

  private List<Long> checklistIdList(Checklist checklist) {
    return checklist.getId() != null ? List.of(checklist.getId()) : List.of();
  }

  private String normalizeTitle(String aiTitle, String originalText) {
    if (aiTitle != null && !aiTitle.isBlank()) {
      return trimToMax(compact(aiTitle), TITLE_MAX_LENGTH);
    }
    return inferTitle(originalText);
  }

  private String normalizeSummary(String aiSummary, String translatedText, String originalText) {
    if (aiSummary != null && !aiSummary.isBlank()) {
      return trimToMax(compact(aiSummary), SUMMARY_MAX_LENGTH);
    }
    return buildBaselineSummary(firstNonBlank(translatedText, originalText));
  }

  private String inferTitle(String originalText) {
    if (originalText == null || originalText.isBlank()) {
      return DEFAULT_TITLE;
    }

    for (String line : originalText.split("\\R")) {
      String compacted = compact(line);
      if (compacted.length() >= 4) {
        return trimToMax(compacted, TITLE_MAX_LENGTH);
      }
    }
    return DEFAULT_TITLE;
  }

  private String buildBaselineSummary(String sourceText) {
    if (sourceText == null || sourceText.isBlank()) {
      return "";
    }
    return trimToMax(compact(sourceText), SUMMARY_MAX_LENGTH);
  }

  private String compact(String text) {
    return text == null ? "" : text.replaceAll("\\s+", " ").trim();
  }

  private String firstNonBlank(String primary, String fallback) {
    return primary != null && !primary.isBlank() ? primary : fallback;
  }

  private String trimNullable(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return trimToMax(compact(value), maxLength);
  }

  private String trimToMax(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3).stripTrailing() + "...";
  }

  private record SavedExtractedItem(ExtractedItem item, Checklist checklist) {}

  public record AiAnalysisResult(String title, String summary) {}
}
