package com.gachi.be.domain.chat.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.AiServerProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** AI 서버 채팅 엔드포인트 클라이언트. */
@Slf4j
@Component
public class AiChatClient {

  private static final String CHAT_PATH = "/ai/chat/messages";

  private final AiServerProperties aiServerProperties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AiChatClient(AiServerProperties aiServerProperties, ObjectMapper objectMapper) {
    this.aiServerProperties = aiServerProperties;
    this.objectMapper = objectMapper;
    // AiNewsletterClient와 동일하게 HTTP/1.1 고정 (HTTP/2 mismatch 방지)
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(aiServerProperties.getConnectTimeoutSeconds()))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /** AI 서버에 채팅 요청. */
  public String chat(
      String message, List<Map<String, String>> history, String language, String chatType,
      DocumentContext document) {
    try {
      String requestBody =
          objectMapper.writeValueAsString(
              new ChatRequest(message, history, language, chatType, document));

      log.info(
          "[AiChatClient] 채팅 요청. language={}, chatType={}, historySize={}, newsletterId={},"
              + " documentLength={}",
          language,
          chatType,
          history.size(),
          document != null ? document.newsletterId() : null,
          document != null && document.originalText() != null
              ? document.originalText().length()
              : 0);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(normalizedBaseUrl() + CHAT_PATH))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .timeout(Duration.ofSeconds(aiServerProperties.getReadTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "[AiChatClient] AI 서버 응답 실패. status={}, body={}",
            response.statusCode(),
            response.body());
        throw new ExternalApiException(
            ErrorCode.CHAT_AI_ERROR, "AI 서버 채팅 실패. status=" + response.statusCode());
      }

      ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
      log.info("[AiChatClient] 채팅 응답 수신 완료.");
      return chatResponse.reply();

    } catch (ExternalApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.CHAT_AI_ERROR, "AI 서버 채팅 통신 인터럽트: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.CHAT_AI_ERROR, "AI 서버 채팅 통신 오류: " + e.getMessage(), e);
    }
  }

  private String normalizedBaseUrl() {
    String baseUrl = aiServerProperties.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new ExternalApiException(ErrorCode.CHAT_AI_ERROR, "AI 서버 base-url이 비어 있습니다.");
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
 // 문서 챗봇(DOCUMENT)에서 AI 서버로 보내는 문서 컨텍스트.
 // AI 서버 schemas.py의 ChatDocumentContext와 필드명이 1:1 대응
  public record DocumentContext(
    Long newsletterId, String title, String summary, String originalText) {}

  // AI 서버로 보내는 요청 body
  record ChatRequest(
      String message, List<Map<String, String>> history, String language, String chatType, DocumentContext document) {}

  // AI 서버에서 받는 응답 body
  record ChatResponse(String reply) {}
}
