package com.gachi.be.domain.school.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.school.dto.response.SchoolSearchItem;
import com.gachi.be.domain.school.dto.response.SchoolSearchResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.NeisProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** NEIS 학교기본정보 Open API를 호출해 학교명 검색 결과를 가져온다. */
@Slf4j
@Component
public class NeisSchoolClient {
  private static final String SUCCESS_CODE = "INFO-000";
  private static final String NO_DATA_CODE = "INFO-200";
  private static final String ELEMENTARY_SCHOOL_KIND = "초등학교";

  private final NeisProperties neisProperties;
  private final ObjectMapper objectMapper;

  public NeisSchoolClient(NeisProperties neisProperties, ObjectMapper objectMapper) {
    this.neisProperties = neisProperties;
    this.objectMapper = objectMapper;
  }

  /** 학교명 키워드로 NEIS 학교기본정보를 조회한다. */
  public SchoolSearchResponse searchByName(String keyword, int size) {
    String normalizedKeyword = keyword.trim();
    try {
      URI searchUri = buildSearchUri(normalizedKeyword, size);
      NeisHttpResponse response = executeGet(searchUri);

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.error(
            "[NEIS] 학교 검색 API 호출 실패. status={}, uri={}, body={}",
            response.statusCode(),
            redactApiKey(searchUri),
            abbreviate(response.body()));
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 API 호출 실패. status=" + response.statusCode());
      }

      return parseResponse(normalizedKeyword, response.body());
    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 API 통신 오류: " + e.getMessage(), e);
    }
  }

  private URI buildSearchUri(String keyword, int size) {
    String apiKey = neisProperties.getApiKey();
    if (!StringUtils.hasText(apiKey)) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 인증키가 설정되지 않았습니다.");
    }

    Map<String, String> queryParams = new LinkedHashMap<>();
    queryParams.put("Type", "json");
    queryParams.put("pIndex", "1");
    queryParams.put("pSize", String.valueOf(size));
    queryParams.put("SCHUL_NM", keyword);
    queryParams.put("SCHUL_KND_SC_NM", ELEMENTARY_SCHOOL_KIND);
    queryParams.put("KEY", apiKey);

    String query =
        queryParams.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    return URI.create(normalizedSchoolInfoUrl() + "?" + query);
  }

  private NeisHttpResponse executeGet(URI uri) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    connection.setRequestMethod("GET");
    // NEIS는 Type=json 쿼리로 응답 포맷을 결정하며 Accept 헤더가 있으면 500을 반환한다.
    connection.setRequestProperty("User-Agent", "GACHI-BE/1.0");
    connection.setConnectTimeout(neisProperties.getConnectTimeoutSeconds() * 1000);
    connection.setReadTimeout(neisProperties.getReadTimeoutSeconds() * 1000);

    try {
      int statusCode = connection.getResponseCode();
      try (var inputStream =
          statusCode >= 200 && statusCode < 300
              ? connection.getInputStream()
              : connection.getErrorStream()) {
        String body =
            inputStream == null
                ? ""
                : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        return new NeisHttpResponse(statusCode, body);
      }
    } finally {
      connection.disconnect();
    }
  }

  private SchoolSearchResponse parseResponse(String keyword, String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode topLevelResult = root.path("RESULT");
      if (!topLevelResult.isMissingNode()) {
        return parseResultOnlyResponse(keyword, topLevelResult);
      }

      JsonNode schoolInfo = root.path("schoolInfo");
      if (!schoolInfo.isArray()) {
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 응답 형식이 올바르지 않습니다.");
      }

      int totalCount = 0;
      List<SchoolSearchItem> schools = new ArrayList<>();
      for (JsonNode section : schoolInfo) {
        totalCount = readTotalCount(section, totalCount);
        validateHeadResult(section);
        JsonNode rows = section.path("row");
        if (rows.isArray()) {
          for (JsonNode row : rows) {
            toItem(row).ifPresent(schools::add);
          }
        }
      }
      return new SchoolSearchResponse(keyword, totalCount, schools);
    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 응답 파싱 실패: " + e.getMessage(), e);
    }
  }

  private SchoolSearchResponse parseResultOnlyResponse(String keyword, JsonNode resultNode) {
    String code = resultNode.path("CODE").asText("");
    if (NO_DATA_CODE.equals(code)) {
      return new SchoolSearchResponse(keyword, 0, List.of());
    }
    throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 API 오류. code=" + code);
  }

  private int readTotalCount(JsonNode section, int currentTotalCount) {
    JsonNode head = section.path("head");
    if (!head.isArray()) {
      return currentTotalCount;
    }
    for (JsonNode headItem : head) {
      if (headItem.has("list_total_count")) {
        return headItem.path("list_total_count").asInt(currentTotalCount);
      }
    }
    return currentTotalCount;
  }

  private void validateHeadResult(JsonNode section) {
    JsonNode head = section.path("head");
    if (!head.isArray()) {
      return;
    }
    for (JsonNode headItem : head) {
      JsonNode result = headItem.path("RESULT");
      if (result.isMissingNode()) {
        continue;
      }
      String code = result.path("CODE").asText("");
      if (!SUCCESS_CODE.equals(code)) {
        throw new ExternalApiException(
            ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교 검색 API 오류. code=" + code);
      }
    }
  }

  private java.util.Optional<SchoolSearchItem> toItem(JsonNode row) {
    String schoolCode = text(row, "SD_SCHUL_CODE");
    String schoolName = text(row, "SCHUL_NM");
    String schoolKind = text(row, "SCHUL_KND_SC_NM");
    if (!StringUtils.hasText(schoolCode) || !StringUtils.hasText(schoolName)) {
      // 학교명/표준학교코드는 선택 저장의 기준값이라 둘 중 하나라도 없으면 내려주지 않는다.
      return java.util.Optional.empty();
    }
    if (!ELEMENTARY_SCHOOL_KIND.equals(schoolKind)) {
      // 서비스 가입 대상이 초등학생이라 외부 응답에 다른 학교급이 섞여도 저장 후보에서 제외한다.
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new SchoolSearchItem(
            schoolCode,
            schoolName,
            text(row, "ENG_SCHUL_NM"),
            schoolKind,
            text(row, "ATPT_OFCDC_SC_CODE"),
            text(row, "ATPT_OFCDC_SC_NM"),
            text(row, "LCTN_SC_NM"),
            text(row, "ORG_RDNMA")));
  }

  private String normalizedSchoolInfoUrl() {
    String url = neisProperties.getSchoolInfoUrl();
    if (!StringUtils.hasText(url)) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 학교기본정보 URL이 설정되지 않았습니다.");
    }
    return url.trim();
  }

  private String text(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);
    if (value.isMissingNode() || value.isNull()) {
      return null;
    }
    String text = value.asText().trim();
    return StringUtils.hasText(text) ? text : null;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String redactApiKey(URI uri) {
    return uri.toString().replaceAll("([?&]KEY=)[^&]*", "$1<hidden>");
  }

  private String abbreviate(String value) {
    if (value == null || value.length() <= 300) {
      return value;
    }
    return value.substring(0, 300);
  }

  private record NeisHttpResponse(int statusCode, String body) {}
}
