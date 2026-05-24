package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.config.external.AiServerProperties;
import com.gachi.be.global.exception.ExternalApiException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiNewsletterClientTest {

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
  void analyzeCallsAnalyzeEndpointAndParsesTitleSummaryItems() throws IOException {
    AtomicReference<String> requestPath = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    startServer();
    server.createContext(
        "/ai/newsletters/analyze",
        exchange -> {
          requestPath.set(exchange.getRequestURI().getPath());
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

          byte[] response =
              """
              {
                "title": "AI 제목",
                "summary": "AI 요약",
                "items": [
                  {
                    "type": "checklist",
                    "title": "동의서 제출",
                    "selectedDateCandidate": null,
                    "datetime": "2026-05-25",
                    "timezone": "Asia/Seoul",
                    "evidenceText": "5월 25일까지 동의서를 제출해 주세요.",
                    "dateStatus": "confirmed",
                    "confidence": 0.9,
                    "needsUserConfirmation": false,
                    "confirmationQuestion": null
                  }
                ],
                "meta": {"mode": "test"}
              }
              """
                  .getBytes(StandardCharsets.UTF_8);
          sendResponse(exchange, 200, response);
        });

    AiNewsletterClient client = newClient(3);

    AnalysisResponse response = client.analyze("원문", "번역문", "KO", List.of());

    assertThat(requestPath.get()).isEqualTo("/ai/newsletters/analyze");
    assertThat(requestBody.get()).contains("\"originalText\":\"원문\"");
    assertThat(response.title()).isEqualTo("AI 제목");
    assertThat(response.summary()).isEqualTo("AI 요약");
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).title()).isEqualTo("동의서 제출");
  }

  @Test
  void analyzeThrowsExternalApiExceptionWhenAiServerReturnsError() throws IOException {
    startServer();
    server.createContext(
        "/ai/newsletters/analyze",
        exchange -> sendResponse(exchange, 500, "{\"detail\":\"server error\"}".getBytes()));

    AiNewsletterClient client = newClient(3);

    assertThatThrownBy(() -> client.analyze("원문", null, "KO", List.of()))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void analyzeThrowsExternalApiExceptionWhenAiServerResponseTimesOut() throws IOException {
    startServer();
    server.createContext(
        "/ai/newsletters/analyze",
        exchange -> {
          try {
            Thread.sleep(1500);
            sendResponse(exchange, 200, "{}".getBytes(StandardCharsets.UTF_8));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    AiNewsletterClient client = newClient(1);

    assertThatThrownBy(() -> client.analyze("원문", null, "KO", List.of()))
        .isInstanceOf(ExternalApiException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
  }

  @Test
  void analyzeThrowsExternalApiExceptionWhenAiServerReturnsMalformedJson() throws IOException {
    startServer();
    server.createContext(
        "/ai/newsletters/analyze",
        exchange -> sendResponse(exchange, 200, "{".getBytes(StandardCharsets.UTF_8)));

    AiNewsletterClient client = newClient(3);

    assertThatThrownBy(() -> client.analyze("원문", null, "KO", List.of()))
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

  private AiNewsletterClient newClient(int readTimeoutSeconds) {
    AiServerProperties properties = new AiServerProperties();
    properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
    properties.setConnectTimeoutSeconds(3);
    properties.setReadTimeoutSeconds(readTimeoutSeconds);
    return new AiNewsletterClient(properties, new ObjectMapper().findAndRegisterModules());
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
