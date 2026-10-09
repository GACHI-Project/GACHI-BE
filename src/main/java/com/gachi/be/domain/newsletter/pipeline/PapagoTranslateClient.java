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
import java.util.ArrayList;
import java.util.List;
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

  /**
   * 여러 문장(이미지 오버레이 블록)을 한 번의 Papago 호출로 번역한다.
   *
   * <p>블록마다 따로 호출하면 페이지당 수십 번 호출이 생기므로, 줄바꿈으로 이어 붙여 1회 호출한 뒤 결과를 다시 줄 단위로 나눈다. Papago는 보통 줄바꿈을
   * 유지하지만, 결과 줄 수가 입력과 다르면(문장 병합 등) 블록 순서가 어긋나므로 그때만 블록별 개별 호출로 대체한다.
   *
   * @return 입력과 같은 순서/개수의 번역 목록. KO이면 null (번역 생략)
   */
  public List<String> translateAll(List<String> originalTexts, String targetLanguage) {
    if ("KO".equals(targetLanguage)) {
      log.debug("[Papago] KO 언어. 블록 번역 스킵.");
      return null;
    }
    if (originalTexts == null || originalTexts.isEmpty()) {
      return List.of();
    }

    String papagoTargetCode = toPapagoCode(targetLanguage);
    // 블록 안의 줄바꿈은 한 줄로 펴서, 결과를 줄 단위로 다시 나눌 때 블록 경계가 흔들리지 않게 한다.
    List<String> singleLineTexts =
        originalTexts.stream()
            .map(text -> text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").strip())
            .toList();

    String joined = String.join("\n", singleLineTexts);
    String translatedJoined = executeTranslation(joined, papagoTargetCode);
    List<String> translatedLines = translatedJoined.lines().map(String::strip).toList();

    if (translatedLines.size() == singleLineTexts.size()) {
      log.debug("[Papago] 블록 일괄 번역 완료. blocks={}", singleLineTexts.size());
      return translatedLines;
    }

    log.warn(
        "[Papago] 블록 일괄 번역 결과 줄 수 불일치. 블록별 개별 번역으로 대체합니다. expected={}, actual={}",
        singleLineTexts.size(),
        translatedLines.size());
    List<String> results = new ArrayList<>();
    for (String text : singleLineTexts) {
      results.add(text.isBlank() ? "" : executeTranslation(text, papagoTargetCode));
    }
    return results;
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "파파고 번역 API 통신 오류: " + e.getMessage());
    } catch (IOException e) {
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
