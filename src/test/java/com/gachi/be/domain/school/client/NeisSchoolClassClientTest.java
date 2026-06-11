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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeisSchoolClassClientTest {
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
  void searchCallsNeisClassInfoAndParsesRows() throws IOException {
    AtomicReference<String> rawQuery = new AtomicReference<>();
    startServer();
    server.createContext(
        "/hub/classInfo",
        exchange -> {
          rawQuery.set(exchange.getRequestURI().getRawQuery());
          sendResponse(
              exchange,
              200,
              """
              {
                "classInfo": [
                  {
                    "head": [
                      {"list_total_count": 2},
                      {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                    ]
                  },
                  {
                    "row": [
                      {
                        "ATPT_OFCDC_SC_CODE": "B10",
                        "SD_SCHUL_CODE": "7051173",
                        "AY": "2026",
                        "GRADE": "4",
                        "SCHUL_CRSE_SC_NM": "초등학교",
                        "CLASS_NM": "1"
                      },
                      {
                        "ATPT_OFCDC_SC_CODE": "B10",
                        "SD_SCHUL_CODE": "7051173",
                        "AY": "2026",
                        "GRADE": "4",
                        "SCHUL_CRSE_SC_NM": "초등학교",
                        "CLASS_NM": "2"
                      }
                    ]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisSchoolClassClient client = newClient("class-key");

    var classes = client.search("B10", "7051173", "2026", 4);

    assertThat(rawQuery.get())
        .contains(
            "KEY=class-key",
            "Type=json",
            "pIndex=1",
            "pSize=1000",
            "ATPT_OFCDC_SC_CODE=B10",
            "SD_SCHUL_CODE=7051173",
            "AY=2026",
            "GRADE=4");
    assertThat(classes).hasSize(2);
    assertThat(classes.get(0).academicYear()).isEqualTo("2026");
    assertThat(classes.get(0).grade()).isEqualTo(4);
    assertThat(classes.get(0).className()).isEqualTo("1");
    assertThat(classes.get(0).schoolCourseName()).isEqualTo("초등학교");
  }

  @Test
  void searchReturnsEmptyResultWhenNeisHasNoData() throws IOException {
    startServer();
    server.createContext(
        "/hub/classInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolClassClient client = newClient("class-key");

    assertThat(client.search("B10", "7051173", "2026", 4)).isEmpty();
  }

  @Test
  void searchContinuesPaginationWhenRowsAreFilteredOut() throws IOException {
    AtomicInteger requestCount = new AtomicInteger();
    startServer();
    server.createContext(
        "/hub/classInfo",
        exchange -> {
          int currentRequest = requestCount.incrementAndGet();
          String rows =
              currentRequest == 1
                  ? """
                    {
                      "row": [
                        {
                          "ATPT_OFCDC_SC_CODE": "B10",
                          "SD_SCHUL_CODE": "7051173",
                          "AY": "2026",
                          "GRADE": "",
                          "CLASS_NM": ""
                        }
                      ]
                    }
                    """
                  : """
                    {
                      "row": [
                        {
                          "ATPT_OFCDC_SC_CODE": "B10",
                          "SD_SCHUL_CODE": "7051173",
                          "AY": "2026",
                          "GRADE": "4",
                          "CLASS_NM": "1"
                        }
                      ]
                    }
                    """;
          sendResponse(
              exchange,
              200,
              ("""
              {
                "classInfo": [
                  {
                    "head": [
                      {"list_total_count": 2},
                      {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                    ]
                  },
                  %s
                ]
              }
              """
                      .formatted(rows))
                  .getBytes(StandardCharsets.UTF_8));
        });

    NeisSchoolClassClient client = newClient("class-key");

    var classes = client.search("B10", "7051173", "2026", 4);

    assertThat(requestCount).hasValue(2);
    assertThat(classes).hasSize(1);
    assertThat(classes.get(0).className()).isEqualTo("1");
  }

  @Test
  void searchThrowsExternalApiExceptionWhenApiKeyIsMissing() {
    NeisSchoolClassClient client = newClient(null);

    assertThatThrownBy(() -> client.search("B10", "7051173", "2026", 4))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void searchThrowsExternalApiExceptionWhenNeisReturnsErrorCode() throws IOException {
    startServer();
    server.createContext(
        "/hub/classInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "ERROR-290", "MESSAGE": "인증키가 유효하지 않습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolClassClient client = newClient("class-key");

    assertThatThrownBy(() -> client.search("B10", "7051173", "2026", 4))
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

  private NeisSchoolClassClient newClient(String apiKey) {
    NeisProperties properties = new NeisProperties();
    properties.setClassInfoApiKey(apiKey);
    if (server != null) {
      properties.setClassInfoUrl(
          "http://localhost:" + server.getAddress().getPort() + "/hub/classInfo");
    }
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(3);
    return new NeisSchoolClassClient(properties, new ObjectMapper());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
