package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.calendar.dto.CalendarPreviewEvent;
import com.gachi.be.domain.calendar.service.CalendarPreviewRedisService;
import com.gachi.be.domain.checklist.entity.Checklist;
import com.gachi.be.domain.checklist.entity.enums.ChecklistType;
import com.gachi.be.domain.checklist.repository.ChecklistRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.ExtractedItem;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.RefineFieldRequest;
import com.gachi.be.domain.newsletter.repository.ConversationTopicRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    Map<String, String> displayTexts =
        translateAndRefineDisplayTexts(originalText, language, analysisResponse, items);

    List<SavedExtractedItem> savedItems =
        saveExtractedItems(newsletterId, newsletter.getUserId(), items, displayTexts, language);
    try {
      saveCalendarPreview(newsletterId, savedItems, displayTexts);
    } catch (RuntimeException e) {
      log.warn(
          "[AiAnalyzer] 캘린더 preview 저장 실패. 분석 결과 저장은 계속 진행합니다. newsletterId={}", newsletterId, e);
    }

    saveConversationTopics(
        newsletterId, newsletter.getUserId(), analysisResponse.conversationTopics(), displayTexts);
    String finalTitle = displayTexts.getOrDefault(FIELD_ID_TITLE, analysisResponse.title());
    String finalSummary = displayTexts.getOrDefault(FIELD_ID_SUMMARY, analysisResponse.summary());
    String rawTitle = normalizeTitle(finalTitle, originalText);
    String summary = normalizeSummary(finalSummary, translatedText, originalText);
    Map<String, String> normalizedTitleI18n =
        trimI18nValues(analysisResponse.titleI18n(), TITLE_MAX_LENGTH);

    log.info(
        "[AiAnalyzer] AI 서버 분석 완료. newsletterId={}, extractedItems={}", newsletterId, items.size());
    return new AiAnalysisResult(rawTitle, normalizedTitleI18n, summary);
  }

  private static final String FIELD_ID_TITLE = "title";
  private static final String FIELD_ID_SUMMARY = "summary";

  /**
   * 1차 분석 응답(한국어)에서 화면에 노출되는 텍스트(title/summary/items[].title/
   * checklistItems[].content,detail/conversationTopics[].topic)를 모아 id를 부여하고, KO가 아니면 Papago로 1차 번역
   * → AI 서버로 2차 검증을 거쳐 최종 텍스트 맵을 반환한다.
   *
   * <p>language=KO이거나 번역 대상 텍스트가 없으면 한국어 원본을 그대로 반환한다 (id → 원본 텍스트).
   */
  private Map<String, String> translateAndRefineDisplayTexts(
      String originalText,
      String language,
      AnalysisResponse analysisResponse,
      List<ExtractedItem> items) {
    String normalizedLanguage =
        (language == null || language.isBlank()) ? "KO" : language.trim().toUpperCase();
    Map<String, String> koTextsById = collectKoreanDisplayTexts(analysisResponse, items);

    if ("KO".equals(normalizedLanguage)) {
      // KO 사용자는 한국어 원본을 그대로 사용 (번역/검증 불필요)
      return koTextsById;
    }

    // 1차: Papago 한국어 → language 번역
    Map<String, String> papagoTextsById = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : koTextsById.entrySet()) {
      String koText = entry.getValue();
      String translated = papagoTranslateClient.translate(koText, normalizedLanguage);
      papagoTextsById.put(entry.getKey(), translated != null ? translated : koText);
    }

    // 2차: AI 서버 검증/교정
    List<RefineFieldRequest> refineFields = new ArrayList<>();
    for (Map.Entry<String, String> entry : koTextsById.entrySet()) {
      refineFields.add(
          new RefineFieldRequest(
              entry.getKey(), entry.getValue(), papagoTextsById.get(entry.getKey())));
    }

    try {
      Map<String, String> refinedById =
          aiNewsletterClient
              .refineTranslation(originalText, normalizedLanguage, refineFields)
              .toMap();

      Map<String, String> result = new LinkedHashMap<>(papagoTextsById);
      result.putAll(refinedById); // 검증 결과로 덮어쓰기. 누락된 id는 파파고 번역값 유지.
      return result;
    } catch (RuntimeException e) {
      // 2차 검증 실패 시 파파고 1차 번역 결과로 폴백 (전체 파이프라인은 계속 진행)
      log.warn("[AiAnalyzer] 2차 검증 실패. 파파고 1차 번역 결과로 대체합니다. error={}", e.getMessage(), e);
      return papagoTextsById;
    }
  }

  /** 1차 분석 응답(한국어)에서 화면 노출 텍스트를 id → 한국어 텍스트 맵으로 수집한다. */
  private Map<String, String> collectKoreanDisplayTexts(
      AnalysisResponse analysisResponse, List<ExtractedItem> items) {
    Map<String, String> texts = new LinkedHashMap<>();

    putIfNotBlank(texts, FIELD_ID_TITLE, analysisResponse.title());
    putIfNotBlank(texts, FIELD_ID_SUMMARY, analysisResponse.summary());

    for (int i = 0; i < items.size(); i++) {
      ExtractedItem item = items.get(i);
      putIfNotBlank(texts, "item_" + i + "_title", item.title());

      if (item.checklistItems() == null) {
        continue;
      }
      for (int j = 0; j < item.checklistItems().size(); j++) {
        AiNewsletterClient.ChecklistItemDto checklistItem = item.checklistItems().get(j);
        putIfNotBlank(texts, "item_" + i + "_chk_" + j + "_content", checklistItem.content());
        putIfNotBlank(texts, "item_" + i + "_chk_" + j + "_detail", checklistItem.detail());
      }
    }

    if (analysisResponse.conversationTopics() != null) {
      for (int k = 0; k < analysisResponse.conversationTopics().size(); k++) {
        putIfNotBlank(texts, "topic_" + k, analysisResponse.conversationTopics().get(k).topic());
      }
    }

    return texts;
  }

  private void putIfNotBlank(Map<String, String> map, String id, String value) {
    if (value != null && !value.isBlank()) {
      map.put(id, value);
    }
  }

  private List<SavedExtractedItem> saveExtractedItems(
      Long newsletterId,
      Long userId,
      List<ExtractedItem> items,
      Map<String, String> displayTexts,
      String language) {
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
      for (int checklistIndex = 0;
          checklistIndex < item.checklistItems().size();
          checklistIndex++) {
        AiNewsletterClient.ChecklistItemDto checklistItem =
            item.checklistItems().get(checklistIndex);
        if (checklistItem.content() == null || checklistItem.content().isBlank()) {
          continue; // 빈 content는 저장하지 않음
        }
        entitiesToSave.add(
            toChecklist(
                newsletterId,
                userId,
                checklistItem,
                itemIndex,
                checklistIndex,
                displayTexts,
                language));
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
      result.add(new SavedExtractedItem(itemIndex, item, linkedChecklists));
    }
    return result;
  }

  private Checklist toChecklist(
      Long newsletterId,
      Long userId,
      AiNewsletterClient.ChecklistItemDto checklistItem,
      int itemIndex,
      int checklistIndex,
      Map<String, String> displayTexts,
      String language) {
    String contentKey = "item_" + itemIndex + "_chk_" + checklistIndex + "_content";
    String detailKey = "item_" + itemIndex + "_chk_" + checklistIndex + "_detail";

    String contentSource = displayTexts.getOrDefault(contentKey, checklistItem.content());
    String content = trimToMax(contentSource.trim(), CHECKLIST_TEXT_MAX_LENGTH);

    String detail = null;
    String detailSource = displayTexts.get(detailKey);
    if (detailSource == null) {
      detailSource = checklistItem.detail();
    }
    if (detailSource != null && !detailSource.isBlank()) {
      detail = trimNullable(detailSource, CHECKLIST_TEXT_MAX_LENGTH);
    }
    Map<String, String> detailI18n;
    if (detail == null) {
      detailI18n = Map.of();
    } else {
      Map<String, String> base =
          new LinkedHashMap<>(
              trimI18nValues(checklistItem.detailI18n(), CHECKLIST_TEXT_MAX_LENGTH));
      String normalizedLanguage =
          (language == null || language.isBlank()) ? "KO" : language.trim().toUpperCase();
      if (!"KO".equals(normalizedLanguage)) {
        String translatedDetail = displayTexts.get(detailKey);
        if (translatedDetail != null && !translatedDetail.isBlank()) {
          base.put(
              normalizedLanguage, trimToMax(compact(translatedDetail), CHECKLIST_TEXT_MAX_LENGTH));
        }
      }
      detailI18n = base;
    }

    return Checklist.builder()
        .newsletterId(newsletterId)
        .calendarEventId(null) // 캘린더 등록 시점에 연결됨 (linkChecklistsToEvents)
        .userId(userId)
        .type(ChecklistType.CHECKLIST) // CHECKLIST만 사용
        .content(content)
        .contentI18n(trimI18nValues(checklistItem.contentI18n(), CHECKLIST_TEXT_MAX_LENGTH))
        .detail(detail)
        .detailI18n(detailI18n)
        .targetDate(null) // 체크리스트는 날짜를 갖지 않음 (일정의 날짜를 따라감)
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

  private void saveCalendarPreview(
      Long newsletterId, List<SavedExtractedItem> savedItems, Map<String, String> displayTexts) {
    List<CalendarPreviewEvent> previewEvents = new ArrayList<>();

    for (SavedExtractedItem savedItem : savedItems) {
      ExtractedItem item = savedItem.item();
      String extractedDate = normalizePreviewDate(item.datetime());
      if (!"confirmed".equalsIgnoreCase(item.dateStatus()) || extractedDate == null) {
        continue;
      }

      String titleSource =
          displayTexts.getOrDefault("item_" + savedItem.itemIndex() + "_title", item.title());

      previewEvents.add(
          new CalendarPreviewEvent(
              "ai_evt_" + (previewEvents.size() + 1),
              trimToMax(titleSource.trim(), CHECKLIST_TEXT_MAX_LENGTH),
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

  private record SavedExtractedItem(
      int itemIndex, ExtractedItem item, List<Checklist> checklists) {}

  public record AiAnalysisResult(String title, Map<String, String> titleI18n, String summary) {}

  private void saveConversationTopics(
      Long newsletterId,
      Long userId,
      List<AiNewsletterClient.ConversationTopicItem> topicItems,
      Map<String, String> displayTexts) {

    if (topicItems == null || topicItems.isEmpty()) {
      log.debug("[AiAnalyzer] 대화 주제 없음. newsletterId={}", newsletterId);
      return;
    }

    List<com.gachi.be.domain.newsletter.entity.ConversationTopic> entities = new ArrayList<>();
    for (int k = 0; k < topicItems.size(); k++) {
      AiNewsletterClient.ConversationTopicItem item = topicItems.get(k);
      if (item.topic() == null || item.topic().isBlank()) {
        continue;
      }
      String topicSource = displayTexts.getOrDefault("topic_" + k, item.topic());
      entities.add(
          com.gachi.be.domain.newsletter.entity.ConversationTopic.builder()
              .newsletterId(newsletterId)
              .userId(userId)
              .topic(topicSource.trim())
              .build());
    }

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
