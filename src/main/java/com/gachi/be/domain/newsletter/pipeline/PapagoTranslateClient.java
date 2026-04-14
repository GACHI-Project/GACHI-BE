package com.gachi.be.domain.newsletter.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.PapagoProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PapagoTranslateClient {

  private final PapagoProperties papagoProperties;
  private final ObjectMapper objectMapper;

  /**
   * 한국어 텍스트를 대상 언어로 번역. KO이면 번역 스킵 → null 반환. DB에 translated_text = NULL로 저장되어 프론트는 originalText를
   * 그대로 표시.
   */
  public String translate(String originalText, String targetLanguage) {
    if ("KO".equals(targetLanguage)) {
      log.debug("[Papago] KO 언어. 번역 스킵.");
      return null;
    }

    if (originalText == null || originalText.isBlank()) {
      log.warn("[Papago] 번역할 텍스트가 비어있습니다.");
      return null;
    }

    String papagoTargetCode = toPapagoCode(targetLanguage);
    log.debug("[Papago] 번역 시작. targetLanguage={}, papagoCode={}", targetLanguage, papagoTargetCode);

    return executeTranslation(originalText, papagoTargetCode);
  }

  private String executeTranslation(String text, String papagoTarget) {
    try {
      // JSON 형식으로 요청 본문 구성 (기존 form-urlencoded → JSON으로 변경)
      String requestBody =
          objectMapper.writeValueAsString(
              Map.of(
                  "source", "ko",
                  "target", papagoTarget,
                  "text", text));

      // 실제 호출 URL: apiUrl + "/translation"
      String apiUrl = papagoProperties.getApiUrl() + "/translation";

      HttpClient httpClient =
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(apiUrl))
              .header("Content-Type", "application/json")
              .header("x-ncp-apigw-api-key-id", papagoProperties.getClientId())
              .header("x-ncp-apigw-api-key", papagoProperties.getClientSecret())
              .POST(HttpRequest.BodyPublishers.ofString(requestBody))
              .timeout(Duration.ofSeconds(30))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error("[Papago] API 호출 실패. status={}, body={}", response.statusCode(), response.body());
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "파파고 번역 API 오류. status=" + response.statusCode());
      }

      return parseTranslationResult(response.body());

    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "파파고 번역 API 통신 오류: " + e.getMessage());
    }
  }

  /** 파파고 번역 응답에서 번역된 텍스트를 추출. */
  private String parseTranslationResult(String responseBody) {
    try {
      PapagoResponse response = objectMapper.readValue(responseBody, PapagoResponse.class);

      if (response.message() == null
          || response.message().result() == null
          || response.message().result().translatedText() == null) {
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR,
            "파파고 응답에서 translatedText를 찾을 수 없습니다. body=" + responseBody);
      }

      String translatedText = response.message().result().translatedText();
      log.debug("[Papago] 번역 완료. length={}", translatedText.length());
      return translatedText;

    } catch (ExternalApiException e) {
      throw e;
    } catch (Exception e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "파파고 번역 응답 파싱 실패: " + e.getMessage());
    }
  }

  private String toPapagoCode(String languageCode) {
    return switch (languageCode) {
      case "US" -> "en";
      case "ZH" -> "zh-CN";
      case "VI" -> "vi";
      default -> {
        log.warn("[Papago] 알 수 없는 언어 코드: {}. 영어로 번역.", languageCode);
        yield "en";
      }
    };
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PapagoResponse(PapagoMessage message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PapagoMessage(PapagoResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PapagoResult(String translatedText) {}
}
