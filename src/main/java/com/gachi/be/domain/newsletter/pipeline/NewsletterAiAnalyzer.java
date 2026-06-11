package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.ExtractedItem;
import com.gachi.be.domain.newsletter.repository.ConversationTopicRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
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
  private final ConversationTopicRepository conversationTopicRepository;

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

    saveConversationTopics(
        newsletterId, newsletter.getUserId(), analysisResponse.conversationTopics());
    String rawTitle = normalizeTitle(analysisResponse.title(), originalText);
    String summary = normalizeSummary(analysisResponse.summary(), translatedText, originalText);

    log.info(
        "[AiAnalyzer] AI 서버 분석 완료. newsletterId={}, extractedItems={}", newsletterId, items.size());
    return new AiAnalysisResult(rawTitle, analysisResponse.titleI18n(), summary);
  }

  private List<SavedExtractedItem> saveExtractedItems(
      Long newsletterId, Long userId, List<ExtractedItem> items, String language) {
    if (items == null || items.isEmpty()) {
      log.warn("[AiAnalyzer] AI 서버 항목 추출 결과 없음. newsletterId={}", newsletterId);
      return List.of();
    }

    List<Checklist> entitiesToSave = new ArrayList<>();
    List<Integer> ownerItemIndex = new ArrayList<>();

    for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
      ExtractedItem item = items.get(itemIndex);
      if (item.checklistItems() == null || item.checklistItems().isEmpty()) {
        continue;
      }
      for (AiNewsletterClient.ChecklistItemDto checklistItem : item.checklistItems()) {
        if (checklistItem.content() == null || checklistItem.content().isBlank()) {
          continue; // 빈 content는 저장하지 않음
        }
        entitiesToSave.add(toChecklist(newsletterId, userId, checklistItem));
        ownerItemIndex.add(itemIndex);
      }
    }

    if (!entitiesToSave.isEmpty()) {
      checklistRepository.saveAll(entitiesToSave);
      log.debug("[AiAnalyzer] AI 서버 추출 체크리스트 {}개 저장 완료.", entitiesToSave.size());
    }

    Map<Integer, List<Checklist>> checklistsByItemIndex = new LinkedHashMap<>();
    for (int i = 0; i < entitiesToSave.size(); i++) {
      checklistsByItemIndex
          .computeIfAbsent(ownerItemIndex.get(i), k -> new ArrayList<>())
          .add(entitiesToSave.get(i));
    }

    List<SavedExtractedItem> result = new ArrayList<>();
    for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
      ExtractedItem item = items.get(itemIndex);
      if (item.title() == null || item.title().isBlank()) {
        // 제목 없는 일정은 캘린더 preview 대상에서 제외 (체크리스트는 이미 저장됨)
        continue;
      }
      List<Checklist> linkedChecklists = checklistsByItemIndex.getOrDefault(itemIndex, List.of());
      result.add(new SavedExtractedItem(item, linkedChecklists));
    }
    return result;
  }

  private Checklist toChecklist(
      Long newsletterId, Long userId, AiNewsletterClient.ChecklistItemDto checklistItem) {
    String content = trimToMax(checklistItem.content().trim(), CHECKLIST_TEXT_MAX_LENGTH);

    String detail = null;
    if (checklistItem.detail() != null && !checklistItem.detail().isBlank()) {
      detail = trimNullable(checklistItem.detail(), CHECKLIST_TEXT_MAX_LENGTH);
    }

    return Checklist.builder()
        .newsletterId(newsletterId)
        .calendarEventId(null) // 캘린더 등록 시점에 연결됨 (linkChecklistsToEvents)
        .userId(userId)
        .type(ChecklistType.CHECKLIST) // v7: CHECKLIST만 사용
        .content(content)
        .contentI18n(trimI18nValues(checklistItem.contentI18n(), CHECKLIST_TEXT_MAX_LENGTH))
        .detail(detail)
        .targetDate(null) // v7: 체크리스트는 날짜를 갖지 않음 (일정의 날짜를 따라감)
        .targetDateLabel(null)
        .build();
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
              trimI18nValues(item.titleI18n(), CHECKLIST_TEXT_MAX_LENGTH),
              extractedDate,
              true,
              checklistIdList(savedItem.checklists())));
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

  private String normalizeTitle(String aiTitle, String originalText) {
    if (aiTitle != null && !aiTitle.isBlank()) {
      return trimToMax(compact(aiTitle), TITLE_MAX_LENGTH);
    }
    return inferTitle(originalText);
  }

  private List<Long> checklistIdList(List<Checklist> checklists) {
    if (checklists == null || checklists.isEmpty()) {
      return List.of();
    }
    return checklists.stream().map(Checklist::getId).filter(Objects::nonNull).toList();
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

  private record SavedExtractedItem(ExtractedItem item, List<Checklist> checklists) {}

  public record AiAnalysisResult(String title, Map<String, String> titleI18n, String summary) {}

  private void saveConversationTopics(
      Long newsletterId, Long userId, List<AiNewsletterClient.ConversationTopicItem> topicItems) {

    if (topicItems == null || topicItems.isEmpty()) {
      log.debug("[AiAnalyzer] 대화 주제 없음. newsletterId={}", newsletterId);
      return;
    }

    List<com.gachi.be.domain.newsletter.entity.ConversationTopic> entities =
        topicItems.stream()
            .filter(item -> item.topic() != null && !item.topic().isBlank())
            .map(
                item -> {
                  return com.gachi.be.domain.newsletter.entity.ConversationTopic.builder()
                      .newsletterId(newsletterId)
                      .userId(userId)
                      .topic(item.topic().trim())
                      .build();
                })
            .toList();

    if (!entities.isEmpty()) {
      conversationTopicRepository.saveAll(entities);
      log.debug("[AiAnalyzer] 대화 주제 {}개 저장 완료. newsletterId={}", entities.size(), newsletterId);
    }
  }

  private Map<String, String> trimI18nValues(Map<String, String> values, int maxLength) {
    if (values == null || values.isEmpty()) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    values.forEach(
        (language, value) -> {
          if (language != null && value != null && !value.isBlank()) {
            result.put(language.trim().toUpperCase(), trimToMax(compact(value), maxLength));
          }
        });
    return result;
  }
}
