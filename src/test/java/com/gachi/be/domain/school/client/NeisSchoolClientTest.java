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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeisSchoolClientTest {

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
  void searchByNameCallsNeisSchoolInfoAndParsesRows() throws IOException {
    AtomicReference<String> rawQuery = new AtomicReference<>();
    startServer();
    server.createContext(
        "/hub/schoolInfo",
        exchange -> {
          rawQuery.set(exchange.getRequestURI().getRawQuery());
          byte[] response =
              """
              {
                "schoolInfo": [
                  {
                    "head": [
                      {"list_total_count": 1},
                      {"RESULT": {"CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다."}}
                    ]
                  },
                  {
                    "row": [
                      {
                        "ATPT_OFCDC_SC_CODE": "B10",
                        "ATPT_OFCDC_SC_NM": "서울특별시교육청",
                        "SD_SCHUL_CODE": "7130118",
                        "SCHUL_NM": "서울까치초등학교",
                        "ENG_SCHUL_NM": "Seoul Kkachi Elementary School",
                        "SCHUL_KND_SC_NM": "초등학교",
                        "LCTN_SC_NM": "서울특별시",
                        "ORG_RDNMA": "서울특별시 노원구 덕릉로79길 23"
                      }
                    ]
                  }
                ]
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          sendResponse(exchange, 200, response);
        });

    NeisSchoolClient client = newClient("test-key");

    var response = client.searchByName("서울까치", 5);

    assertThat(rawQuery.get()).contains("KEY=test-key", "Type=json", "pIndex=1", "pSize=5");
    assertThat(rawQuery.get()).contains("SCHUL_NM=%EC%84%9C%EC%9A%B8%EA%B9%8C%EC%B9%98");
    assertThat(rawQuery.get()).contains("SCHUL_KND_SC_NM=%EC%B4%88%EB%93%B1%ED%95%99%EA%B5%90");
    assertThat(response.keyword()).isEqualTo("서울까치");
    assertThat(response.totalCount()).isEqualTo(1);
    assertThat(response.schools()).hasSize(1);
    assertThat(response.schools().get(0).schoolCode()).isEqualTo("7130118");
    assertThat(response.schools().get(0).schoolName()).isEqualTo("서울까치초등학교");
    assertThat(response.schools().get(0).englishSchoolName())
        .isEqualTo("Seoul Kkachi Elementary School");
    assertThat(response.schools().get(0).schoolKind()).isEqualTo("초등학교");
    assertThat(response.schools().get(0).officeName()).isEqualTo("서울특별시교육청");
  }

  @Test
  void searchByNameReturnsEmptyResultWhenNeisHasNoData() throws IOException {
    startServer();
    server.createContext(
        "/hub/schoolInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {"RESULT": {"CODE": "INFO-200", "MESSAGE": "해당하는 데이터가 없습니다."}}
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolClient client = newClient("test-key");

    var response = client.searchByName("없는학교", 10);

    assertThat(response.keyword()).isEqualTo("없는학교");
    assertThat(response.totalCount()).isZero();
    assertThat(response.schools()).isEmpty();
  }

  @Test
  void searchByNameFiltersOutNonElementarySchools() throws IOException {
    startServer();
    server.createContext(
        "/hub/schoolInfo",
        exchange ->
            sendResponse(
                exchange,
                200,
                """
                {
                  "schoolInfo": [
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
                          "ATPT_OFCDC_SC_NM": "서울특별시교육청",
                          "SD_SCHUL_CODE": "7130118",
                          "SCHUL_NM": "서울까치초등학교",
                          "ENG_SCHUL_NM": "Seoul Kkachi Elementary School",
                          "SCHUL_KND_SC_NM": "초등학교",
                          "LCTN_SC_NM": "서울특별시",
                          "ORG_RDNMA": "서울특별시 노원구 덕릉로79길 23"
                        },
                        {
                          "ATPT_OFCDC_SC_CODE": "B10",
                          "ATPT_OFCDC_SC_NM": "서울특별시교육청",
                          "SD_SCHUL_CODE": "7130165",
                          "SCHUL_NM": "가락중학교",
                          "SCHUL_KND_SC_NM": "중학교",
                          "LCTN_SC_NM": "서울특별시",
                          "ORG_RDNMA": "서울특별시 송파구 송이로 45"
                        }
                      ]
                    }
                  ]
                }
                """
                    .getBytes(StandardCharsets.UTF_8)));

    NeisSchoolClient client = newClient("test-key");

    var response = client.searchByName("서울", 10);

    assertThat(response.schools()).hasSize(1);
    assertThat(response.schools().get(0).schoolName()).isEqualTo("서울까치초등학교");
    assertThat(response.schools().get(0).schoolKind()).isEqualTo("초등학교");
  }

  @Test
  void searchByNameThrowsExternalApiExceptionWhenNeisReturnsServerError() throws IOException {
    startServer();
    server.createContext(
        "/hub/schoolInfo",
        exchange -> sendResponse(exchange, 500, "{\"error\":\"server\"}".getBytes()));

    NeisSchoolClient client = newClient("test-key");

    assertThatThrownBy(() -> client.searchByName("서울까치", 10))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void searchByNameThrowsExternalApiExceptionWhenApiKeyIsMissing() {
    NeisSchoolClient client = newClient("");

    assertThatThrownBy(() -> client.searchByName("서울까치", 10))
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

  private NeisSchoolClient newClient(String apiKey) {
    NeisProperties properties = new NeisProperties();
    properties.setApiKey(apiKey);
    if (server != null) {
      properties.setSchoolInfoUrl(
          "http://localhost:" + server.getAddress().getPort() + "/hub/schoolInfo");
    }
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(3);
    return new NeisSchoolClient(properties, new ObjectMapper());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
