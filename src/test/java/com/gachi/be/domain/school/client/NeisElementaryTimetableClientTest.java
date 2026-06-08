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

class NeisElementaryTimetableClientTest {
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
  void searchCallsNeisElsTimetableAndParsesRows() throws IOException {
    AtomicReference<String> rawQuery = new AtomicReference<>();
    startServer();
    server.createContext(
        "/hub/elsTimetable",
        exchange -> {
          rawQuery.set(exchange.getRequestURI().getRawQuery());
          sendResponse(
              exchange,
              200,
              """
              {
                "elsTimetable": [
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
                        "SEM": "1",
                        "ALL_TI_YMD": "20260302",
                        "GRADE": "4",
                        "CLASS_NM": "1",
                        "PERIO": "2",
                        "ITRT_CNTNT": "수학"
                      }
                    ]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisElementaryTimetableClient client = newClient("timetable-key");

    var timetables =
        client.search(
            "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 4, "1");

    assertThat(rawQuery.get())
        .contains(
            "KEY=timetable-key",
            "Type=json",
            "pIndex=1",
            "pSize=1000",
            "ATPT_OFCDC_SC_CODE=B10",
            "SD_SCHUL_CODE=7051173",
            "TI_FROM_YMD=20260301",
            "TI_TO_YMD=20260331",
            "GRADE=4",
            "CLASS_NM=1");
    assertThat(timetables).hasSize(1);
    assertThat(timetables.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(timetables.get(0).grade()).isEqualTo(4);
    assertThat(timetables.get(0).className()).isEqualTo("1");
    assertThat(timetables.get(0).period()).isEqualTo(2);
    assertThat(timetables.get(0).content()).isEqualTo("수학");
  }

  @Test
  void searchReturnsEmptyResultWhenNeisHasNoData() throws IOException {
    startServer();
    server.createContext(
        "/hub/elsTimetable",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisElementaryTimetableClient client = newClient("timetable-key");

    var timetables =
        client.search(
            "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 4, "1");

    assertThat(timetables).isEmpty();
  }

  @Test
  void searchThrowsExternalApiExceptionWhenTimetableApiKeyIsMissing() {
    NeisElementaryTimetableClient client = newClient(null);

    assertThatThrownBy(
            () ->
                client.search(
                    "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 4, "1"))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void searchThrowsExternalApiExceptionWhenNeisReturnsErrorCode() throws IOException {
    startServer();
    server.createContext(
        "/hub/elsTimetable",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "ERROR-290", "MESSAGE": "인증키가 유효하지 않습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisElementaryTimetableClient client = newClient("timetable-key");

    assertThatThrownBy(
            () ->
                client.search(
                    "B10", "7051173", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 4, "1"))
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

  private NeisElementaryTimetableClient newClient(String apiKey) {
    NeisProperties properties = new NeisProperties();
    properties.setTimetableApiKey(apiKey);
    if (server != null) {
      properties.setElementaryTimetableUrl(
          "http://localhost:" + server.getAddress().getPort() + "/hub/elsTimetable");
    }
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(3);
    return new NeisElementaryTimetableClient(properties, new ObjectMapper());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
