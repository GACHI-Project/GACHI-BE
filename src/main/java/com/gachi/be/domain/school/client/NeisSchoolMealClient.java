package com.gachi.be.domain.school.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.school.dto.response.NeisSchoolMealItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.NeisProperties;
import com.gachi.be.global.exception.ExternalApiException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** NEIS 급식식단정보 Open API를 호출해 학교별 급식표를 가져온다. */
@Slf4j
@Component
public class NeisSchoolMealClient {
  private static final int PAGE_SIZE = 1000;
  private static final String SUCCESS_CODE = "INFO-000";
  private static final String NO_DATA_CODE = "INFO-200";
  private static final DateTimeFormatter NEIS_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

  private final NeisProperties neisProperties;
  private final ObjectMapper objectMapper;

  public NeisSchoolMealClient(NeisProperties neisProperties, ObjectMapper objectMapper) {
    this.neisProperties = neisProperties;
    this.objectMapper = objectMapper;
  }

  /** 학교 식별값과 기간으로 NEIS 급식표를 조회한다. */
  public List<NeisSchoolMealItem> search(
      String officeCode, String schoolCode, LocalDate fromDate, LocalDate toDate) {
    List<NeisSchoolMealItem> meals = new ArrayList<>();
    int pageIndex = 1;
    int totalCount = Integer.MAX_VALUE;

    try {
      while (meals.size() < totalCount) {
        URI uri = buildSearchUri(officeCode, schoolCode, fromDate, toDate, pageIndex);
        NeisHttpResponse response = executeGet(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          log.error(
              "[NEIS] 급식표 API 호출 실패. status={}, uri={}, body={}",
              response.statusCode(),
              redactApiKey(uri),
              abbreviate(response.body()));
          throw new ExternalApiException(
              ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 API 호출 실패. status=" + response.statusCode());
        }

        ParsedMealPage parsedPage = parseResponse(response.body());
        totalCount = parsedPage.totalCount();
        if (parsedPage.rowCount() == 0) {
          break;
        }
        meals.addAll(parsedPage.meals());
        pageIndex++;
      }
      return List.copyOf(meals);
    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 API 통신 오류: " + e.getMessage(), e);
    }
  }

  private URI buildSearchUri(
      String officeCode, String schoolCode, LocalDate fromDate, LocalDate toDate, int pageIndex) {
    String apiKey = neisProperties.getMealApiKey();
    if (!StringUtils.hasText(apiKey)) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 인증키가 설정되지 않았습니다.");
    }

    Map<String, String> queryParams = new LinkedHashMap<>();
    queryParams.put("Type", "json");
    queryParams.put("pIndex", String.valueOf(pageIndex));
    queryParams.put("pSize", String.valueOf(PAGE_SIZE));
    queryParams.put("ATPT_OFCDC_SC_CODE", officeCode);
    queryParams.put("SD_SCHUL_CODE", schoolCode);
    queryParams.put("MLSV_FROM_YMD", fromDate.format(NEIS_DATE_FORMATTER));
    queryParams.put("MLSV_TO_YMD", toDate.format(NEIS_DATE_FORMATTER));
    queryParams.put("KEY", apiKey);

    String query =
        queryParams.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    return URI.create(normalizedMealServiceDietInfoUrl() + "?" + query);
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

  private ParsedMealPage parseResponse(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode topLevelResult = root.path("RESULT");
      if (!topLevelResult.isMissingNode()) {
        return parseResultOnlyResponse(topLevelResult);
      }

      JsonNode mealServiceDietInfo = root.path("mealServiceDietInfo");
      if (!mealServiceDietInfo.isArray()) {
        throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 응답 형식이 올바르지 않습니다.");
      }

      int totalCount = 0;
      int rowCount = 0;
      List<NeisSchoolMealItem> meals = new ArrayList<>();
      for (JsonNode section : mealServiceDietInfo) {
        totalCount = readTotalCount(section, totalCount);
        validateHeadResult(section);
        JsonNode rows = section.path("row");
        if (rows.isArray()) {
          rowCount += rows.size();
          for (JsonNode row : rows) {
            toItem(row).ifPresent(meals::add);
          }
        }
      }
      return new ParsedMealPage(totalCount, rowCount, meals);
    } catch (ExternalApiException e) {
      throw e;
    } catch (IOException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 응답 파싱 실패: " + e.getMessage(), e);
    }
  }

  private ParsedMealPage parseResultOnlyResponse(JsonNode resultNode) {
    String code = resultNode.path("CODE").asText("");
    if (NO_DATA_CODE.equals(code)) {
      return new ParsedMealPage(0, 0, List.of());
    }
    throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 API 오류. code=" + code);
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
            ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 API 오류. code=" + code);
      }
    }
  }

  private java.util.Optional<NeisSchoolMealItem> toItem(JsonNode row) {
    LocalDate date = parseNeisDate(text(row, "MLSV_YMD"));
    String dishName = normalizeMultiline(text(row, "DDISH_NM"));
    if (date == null || !StringUtils.hasText(dishName)) {
      // 급식표 화면의 최소 단위는 날짜와 메뉴라 둘 중 하나라도 없으면 제외한다.
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new NeisSchoolMealItem(
            date,
            text(row, "MMEAL_SC_CODE"),
            text(row, "MMEAL_SC_NM"),
            parseInteger(text(row, "MLSV_FGR")),
            dishName,
            normalizeMultiline(text(row, "ORPLC_INFO")),
            text(row, "CAL_INFO"),
            normalizeMultiline(text(row, "NTR_INFO"))));
  }

  private LocalDate parseNeisDate(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return LocalDate.parse(value, NEIS_DATE_FORMATTER);
    } catch (DateTimeParseException e) {
      log.warn("[NEIS] 급식표 날짜 형식 오류로 해당 행 제외. value={}", value);
      return null;
    }
  }

  private Integer parseInteger(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      log.warn("[NEIS] 급식 인원수 형식 오류. value={}", value);
      return null;
    }
  }

  private String normalizeMultiline(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.replaceAll("(?i)<br\\s*/?>", "\n").trim();
  }

  private String normalizedMealServiceDietInfoUrl() {
    String url = neisProperties.getMealServiceDietInfoUrl();
    if (!StringUtils.hasText(url)) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR, "NEIS 급식표 URL이 설정되지 않았습니다.");
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

  private record ParsedMealPage(int totalCount, int rowCount, List<NeisSchoolMealItem> meals) {}

  private record NeisHttpResponse(int statusCode, String body) {}
}
