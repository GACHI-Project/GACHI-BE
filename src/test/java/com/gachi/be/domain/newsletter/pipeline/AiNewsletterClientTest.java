package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.global.config.external.AiServerProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiNewsletterClientTest {

  @Test
  void analyzeCallsAnalyzeEndpointAndParsesTitleSummaryItems() throws IOException {
    AtomicReference<String> requestPath = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    ExecutorService executor = Executors.newSingleThreadExecutor();
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
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.setExecutor(executor);
    server.start();

    try {
      AiServerProperties properties = new AiServerProperties();
      properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
      properties.setConnectTimeoutSeconds(3);
      properties.setReadTimeoutSeconds(3);
      AiNewsletterClient client =
          new AiNewsletterClient(properties, new ObjectMapper().findAndRegisterModules());

      AnalysisResponse response = client.analyze("원문", "번역문", "KO", List.of());

      assertThat(requestPath.get()).isEqualTo("/ai/newsletters/analyze");
      assertThat(requestBody.get()).contains("\"originalText\":\"원문\"");
      assertThat(response.title()).isEqualTo("AI 제목");
      assertThat(response.summary()).isEqualTo("AI 요약");
      assertThat(response.items()).hasSize(1);
      assertThat(response.items().get(0).title()).isEqualTo("동의서 제출");
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }
}
