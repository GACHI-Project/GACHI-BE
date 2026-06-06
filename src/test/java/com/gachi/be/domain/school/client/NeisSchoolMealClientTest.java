package com.gachi.be.domain.school.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.NeisProperties;
import com.gachi.be.global.exception.ExternalApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeisSchoolMealClientTest {
  private HttpServer server;
  private ExecutorService executor;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Test
  void searchCallsNeisMealServiceDietInfoAndParsesRows() throws IOException {
    AtomicReference<String> rawQuery = new AtomicReference<>();
    startServer();
    server.createContext(
        "/hub/mealServiceDietInfo",
        exchange -> {
          rawQuery.set(exchange.getRequestURI().getRawQuery());
          sendResponse(
              exchange,
              200,
              """
              {
                "mealServiceDietInfo": [
                  {
                    "head": [
                      {"list_total_count": 1},
                      {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                    ]
                  },
                  {
                    "row": [
                      {
                        "MMEAL_SC_CODE": "2",
                        "MMEAL_SC_NM": "중식",
                        "MLSV_YMD": "20260302",
                        "MLSV_FGR": "123",
                        "DDISH_NM": "현미밥<br/>미역국",
                        "ORPLC_INFO": "쌀 : 국내산",
                        "CAL_INFO": "612.3 Kcal",
                        "NTR_INFO": "탄수화물(g) : 90.1<br/>단백질(g) : 22.3"
                      }
                    ]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisSchoolMealClient client = newClient("meal-key");

    var meals =
        client.search("B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(rawQuery.get())
        .contains(
            "KEY=meal-key",
            "Type=json",
            "pIndex=1",
            "pSize=1000",
            "ATPT_OFCDC_SC_CODE=B10",
            "SD_SCHUL_CODE=7051173",
            "MLSV_FROM_YMD=20260301",
            "MLSV_TO_YMD=20260331");
    assertThat(meals).hasSize(1);
    assertThat(meals.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(meals.get(0).mealName()).isEqualTo("중식");
    assertThat(meals.get(0).mealPeopleCount()).isEqualTo(123);
    assertThat(meals.get(0).dishName()).isEqualTo("현미밥\n미역국");
  }

  @Test
  void searchReturnsEmptyResultWhenNeisHasNoData() throws IOException {
    startServer();
    server.createContext(
        "/hub/mealServiceDietInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolMealClient client = newClient("meal-key");

    var meals =
        client.search("B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(meals).isEmpty();
  }

  @Test
  void searchThrowsExternalApiExceptionWhenMealApiKeyIsMissing() {
    NeisSchoolMealClient client = newClient(null);

    assertThatThrownBy(
            () ->
                client.search(
                    "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void searchThrowsExternalApiExceptionWhenNeisReturnsErrorCode() throws IOException {
    startServer();
    server.createContext(
        "/hub/mealServiceDietInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "ERROR-290", "MESSAGE": "인증키가 유효하지 않습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolMealClient client = newClient("meal-key");

    assertThatThrownBy(
            () ->
                client.search(
                    "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  private void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    executor = Executors.newSingleThreadExecutor();
    server.setExecutor(executor);
    server.start();
  }

  private NeisSchoolMealClient newClient(String apiKey) {
    NeisProperties properties = new NeisProperties();
    properties.setMealApiKey(apiKey);
    if (server != null) {
      properties.setMealServiceDietInfoUrl(
          "http://localhost:" + server.getAddress().getPort() + "/hub/mealServiceDietInfo");
    }
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(3);
    return new NeisSchoolMealClient(properties, new ObjectMapper());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
