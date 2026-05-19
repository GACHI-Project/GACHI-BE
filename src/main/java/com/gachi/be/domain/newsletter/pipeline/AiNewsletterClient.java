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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiNewsletterClient {

  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final String EXTRACT_ITEMS_PATH = "/ai/newsletters/extract-items";

  private final AiServerProperties aiServerProperties;
  private final ObjectMapper objectMapper;

  public List<ExtractedItem> extractItems(
      String originalText,
      String translatedText,
      String language,
      List<NewsletterDateCandidate> dateCandidates) {
    try {
      String requestBody =
          objectMapper.writeValueAsString(
              new ExtractionRequest(
                  originalText,
                  translatedText,
                  language != null ? language : "KO",
                  LocalDate.now(DEFAULT_ZONE),
                  DEFAULT_ZONE.getId(),
                  toDateCandidateRequests(dateCandidates)));

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(normalizedBaseUrl() + EXTRACT_ITEMS_PATH))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(aiServerProperties.getReadTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      HttpClient httpClient =
          HttpClient.newBuilder()
              .connectTimeout(Duration.ofSeconds(aiServerProperties.getConnectTimeoutSeconds()))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "[AiNewsletterClient] AI 서버 항목 추출 실패. status={}, body={}",
            response.statusCode(),
            response.body());
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "AI 서버 항목 추출 실패. status=" + response.statusCode());
      }

      ExtractionResponse extractionResponse =
          objectMapper.readValue(response.body(), ExtractionResponse.class);
      return extractionResponse.items() != null ? extractionResponse.items() : List.of();
    } catch (ExternalApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "AI 서버 통신 인터럽트: " + e.getMessage());
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "AI 서버 통신 오류: " + e.getMessage());
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

  record ExtractionRequest(
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
  record ExtractionResponse(List<ExtractedItem> items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExtractedItem(
      String type,
      String title,
      String datetime,
      String timezone,
      String evidenceText,
      String dateStatus,
      Double confidence,
      Boolean needsUserConfirmation,
      String confirmationQuestion) {}
}
