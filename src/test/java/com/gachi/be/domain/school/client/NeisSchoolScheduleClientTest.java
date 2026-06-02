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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeisSchoolScheduleClientTest {
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
  void searchCallsNeisSchoolScheduleAndParsesRows() throws IOException {
    AtomicReference<String> rawQuery = new AtomicReference<>();
    startServer();
    server.createContext(
        "/hub/SchoolSchedule",
        exchange -> {
          rawQuery.set(exchange.getRequestURI().getRawQuery());
          sendResponse(
              exchange,
              200,
              """
              {
                "SchoolSchedule": [
                  {
                    "head": [
                      {"list_total_count": 1},
                      {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                    ]
                  },
                  {
                    "row": [
                      {
                        "AY": "2026",
                        "AA_YMD": "20260302",
                        "EVENT_NM": "시업식",
                        "EVENT_CNTNT": "1학기 시작",
                        "ONE_GRADE_EVENT_YN": "Y",
                        "TW_GRADE_EVENT_YN": "Y",
                        "THREE_GRADE_EVENT_YN": "Y",
                        "FR_GRADE_EVENT_YN": "Y",
                        "FIV_GRADE_EVENT_YN": "Y",
                        "SIX_GRADE_EVENT_YN": "Y"
                      }
                    ]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisSchoolScheduleClient client = newClient("test-key");

    var schedules =
        client.search("B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(rawQuery.get())
        .contains(
            "KEY=test-key",
            "Type=json",
            "pIndex=1",
            "pSize=1000",
            "ATPT_OFCDC_SC_CODE=B10",
            "SD_SCHUL_CODE=7051173",
            "AA_FROM_YMD=20260301",
            "AA_TO_YMD=20260331");
    assertThat(schedules).hasSize(1);
    assertThat(schedules.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(schedules.get(0).eventName()).isEqualTo("시업식");
    assertThat(schedules.get(0).gradeEventYn().grade1()).isEqualTo("Y");
  }

  @Test
  void searchReturnsEmptyResultWhenNeisHasNoData() throws IOException {
    startServer();
    server.createContext(
        "/hub/SchoolSchedule",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolScheduleClient client = newClient("test-key");

    var schedules =
        client.search("B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(schedules).isEmpty();
  }

  @Test
  void searchContinuesPaginationWhenCurrentPageRowsAreFilteredOut() throws IOException {
    AtomicInteger requestCount = new AtomicInteger();
    startServer();
    server.createContext(
        "/hub/SchoolSchedule",
        exchange -> {
          int pageIndex = requestCount.incrementAndGet();
          if (pageIndex == 1) {
            sendResponse(
                exchange,
                200,
                """
                {
                  "SchoolSchedule": [
                    {
                      "head": [
                        {"list_total_count": 2},
                        {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                      ]
                    },
                    {
                      "row": [
                        {"AY": "2026", "AA_YMD": "invalid-date", "EVENT_NM": "잘못된 날짜"}
                      ]
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8));
            return;
          }
          if (pageIndex == 2) {
            sendResponse(
                exchange,
                200,
                """
                {
                  "SchoolSchedule": [
                    {
                      "head": [
                        {"list_total_count": 2},
                        {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                      ]
                    },
                    {
                      "row": [
                        {"AY": "2026", "AA_YMD": "20260305", "EVENT_NM": "재량휴업일"}
                      ]
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8));
            return;
          }
          sendResponse(
              exchange,
              200,
              """
              {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
              """
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisSchoolScheduleClient client = newClient("test-key");

    var schedules =
        client.search("B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(schedules).hasSize(1);
    assertThat(schedules.get(0).eventName()).isEqualTo("재량휴업일");
    assertThat(requestCount).hasValue(3);
  }

  @Test
  void searchThrowsExternalApiExceptionWhenNeisReturnsServerError() throws IOException {
    startServer();
    server.createContext(
        "/hub/SchoolSchedule",
        exchange -> sendResponse(exchange, 500, "{\"error\":\"server\"}".getBytes()));

    NeisSchoolScheduleClient client = newClient("test-key");

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

  private NeisSchoolScheduleClient newClient(String apiKey) {
    NeisProperties properties = new NeisProperties();
    properties.setScheduleApiKey(apiKey);
    if (server != null) {
      properties.setSchoolScheduleUrl(
          "http://localhost:" + server.getAddress().getPort() + "/hub/SchoolSchedule");
    }
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(3);
    return new NeisSchoolScheduleClient(properties, new ObjectMapper());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
