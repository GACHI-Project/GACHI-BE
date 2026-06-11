package com.gachi.be.domain.newsletter.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.entity.NewsletterDateCandidate;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.AiServerProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiNewsletterClient {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final String ANALYZE_PATH = "/ai/newsletters/analyze";

  private final AiServerProperties aiServerProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AiNewsletterClient(AiServerProperties aiServerProperties, ObjectMapper objectMapper) {
    this.aiServerProperties = aiServerProperties;
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(aiServerProperties.getConnectTimeoutSeconds()))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  public AnalysisResponse analyze(
      String originalText,
      String translatedText,
      String language,
      List<NewsletterDateCandidate> dateCandidates) {
    try {
      String requestBody =
          objectMapper.writeValueAsString(
              new AnalysisRequest(
                  originalText,
                  translatedText,
                  language != null ? language : "KO",
                  LocalDate.now(DEFAULT_ZONE),
                  DEFAULT_ZONE.getId(),
                  toDateCandidateRequests(dateCandidates)));
      log.info("[AiNewsletterClient] 요청 body: {}", requestBody);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(normalizedBaseUrl() + ANALYZE_PATH))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(aiServerProperties.getReadTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "[AiNewsletterClient] AI 서버 분석 실패. status={}, body={}",
            response.statusCode(),
            response.body());
        // response.body() != null ? response.body().length() : 0);
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "AI 서버 분석 실패. status=" + response.statusCode());
      }

      return objectMapper.readValue(response.body(), AnalysisResponse.class).normalized();
    } catch (ExternalApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "AI 서버 통신 인터럽트: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "AI 서버 통신 오류: " + e.getMessage(), e);
    }
  }

  private String normalizedBaseUrl() {
    String baseUrl = aiServerProperties.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "AI 서버 base-url이 비어 있습니다.");
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }

  private List<DateCandidateRequest> toDateCandidateRequests(
      List<NewsletterDateCandidate> dateCandidates) {
    if (dateCandidates == null || dateCandidates.isEmpty()) {
      return List.of();
    }

    List<DateCandidateRequest> requests = new ArrayList<>();
    for (int i = 0; i < dateCandidates.size(); i++) {
      NewsletterDateCandidate candidate = dateCandidates.get(i);
      requests.add(
          new DateCandidateRequest(
              "dc_" + (i + 1),
              candidate.originalText(),
              candidate.normalizedDate(),
              candidate.startOffset(),
              candidate.endOffset(),
              candidate.extractionType() != null ? candidate.extractionType().name() : null));
    }
    return requests;
  }

  record AnalysisRequest(
      String originalText,
      String translatedText,
      String language,
      LocalDate referenceDate,
      String timezone,
      List<DateCandidateRequest> dateCandidates) {}

  record DateCandidateRequest(
      String candidateId,
      String originalText,
      LocalDate normalizedDate,
      int startOffset,
      int endOffset,
      String extractionType) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AnalysisResponse(
      String title,
      String summary,
      List<ExtractedItem> items,
      List<ConversationTopicItem> conversationTopics,
      Map<String, Object> meta) {

    AnalysisResponse normalized() {
      List<ExtractedItem> normalizedItems =
          items != null ? items.stream().map(ExtractedItem::normalized).toList() : List.of();
      return new AnalysisResponse(
          title,
          summary,
          items != null ? items : List.of(),
          conversationTopics != null ? conversationTopics : List.of(),
          meta);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConversationTopicItem(String topic) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SelectedDateCandidate(
      Integer index, String candidateId, String originalText, LocalDate normalizedDate) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ChecklistItemDto(String content, String detail) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractedItem(
      String type,
      String title,
      SelectedDateCandidate selectedDateCandidate,
      String datetime,
      String timezone,
      String evidenceText,
      String dateStatus,
      Double confidence,
      Boolean needsUserConfirmation,
      String confirmationQuestion,
      List<ChecklistItemDto> checklistItems) {

      ExtractedItem normalized() {
          return new ExtractedItem(
              type,
              title,
              selectedDateCandidate,
              datetime,
              timezone,
              evidenceText,
              dateStatus,
              confidence,
              needsUserConfirmation,
              confirmationQuestion,
              checklistItems != null ? checklistItems : List.of());
      }
  }
}
