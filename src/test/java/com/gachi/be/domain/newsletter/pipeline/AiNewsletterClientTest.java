package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.AnalysisResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.DocumentSource;
import com.gachi.be.file.config.S3Properties;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class AiNewsletterClientTest {

  private HttpServer server;
  private ExecutorService executor;
  private S3Presigner s3Presigner;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
    if (s3Presigner != null) {
        s3Presigner.close();
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
                      "type": "reminder",
                      "title": "동의서 제출",
                      "selectedDateCandidate": null,
                      "datetime": "2026-05-25",
                      "timezone": "Asia/Seoul",
                      "evidenceText": "5월 25일까지 동의서를 제출해 주세요.",
                      "dateStatus": "confirmed",
                      "confidence": 0.9,
                      "needsUserConfirmation": false,
                      "confirmationQuestion": null,
                      "checklistItems": []
                    }
                  ],
                  "meta": {"mode": "test"}
                }
                """
                  .getBytes(StandardCharsets.UTF_8);
          sendResponse(exchange, 200, response);
        });

    AiNewsletterClient client = newClient(3);

    AnalysisResponse response = client.analyze("원문", "번역문", "KO", List.of(), List.of());

    assertThat(requestPath.get()).isEqualTo("/ai/newsletters/analyze");
    assertThat(requestBody.get()).contains("\"originalText\":\"원문\"");
    assertThat(response.title()).isEqualTo("AI 제목");
    assertThat(response.summary()).isEqualTo("AI 요약");
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).title()).isEqualTo("동의서 제출");
    assertThat(requestBody.get()).contains("\"documents\":[]");
  }

  // 원본 문서가 페이지 순서대로 Presigned URL, 파일명, 형식과 함께 전송되는지 검증
  @Test
  void analyzeSendsDocumentsWithPresignedUrlsInPageOrder() throws IOException {
      AtomicReference<String> requestBody = new AtomicReference<>();
      startServer();
      server.createContext(
          "/ai/newsletters/analyze",
          exchange -> {
              requestBody.set(
                  new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
              sendResponse(
                  exchange,
                  200,
                  "{\"title\":\"AI 제목\",\"summary\":\"AI 요약\",\"items\":[]}"
                      .getBytes(StandardCharsets.UTF_8));
          });

      AiNewsletterClient client = newClient(3);

      client.analyze(
          "원문",
          null,
          "KO",
          List.of(),
          List.of(
              new DocumentSource("newsletters/page1.pdf", "application/pdf"),
              new DocumentSource("newsletters/page2.jpg_processed_uuid", "image/png")));

      String body = requestBody.get();
      assertThat(body).contains("\"fileName\":\"newsletter-page-1.pdf\"");
      assertThat(body).contains("\"fileName\":\"newsletter-page-2.png\"");
      assertThat(body).contains("\"mimeType\":\"application/pdf\"");
      assertThat(body).contains("\"mimeType\":\"image/png\"");
      assertThat(body).contains("test-bucket");
      assertThat(body).contains("X-Amz-Signature");
      assertThat(body.indexOf("newsletter-page-1.pdf"))
          .isLessThan(body.indexOf("newsletter-page-2.png"));
  }

  @Test
  void analyzeThrowsExternalApiExceptionWhenAiServerReturnsError() throws IOException {
    startServer();
    server.createContext(
        "/ai/newsletters/analyze",
        exchange -> sendResponse(exchange, 500, "{\"detail\":\"server error\"}".getBytes()));

    AiNewsletterClient client = newClient(3);

    assertThatThrownBy(() -> client.analyze("원문", null, "KO", List.of(), List.of()))
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

    assertThatThrownBy(() -> client.analyze("원문", null, "KO", List.of(), List.of()))
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
    // 테스트용 S3 설정. 서명만 로컬에서 생성하므로 더미 자격증명을 사용한다.
    S3Properties s3Properties = new S3Properties();
    s3Properties.setBucket("test-bucket");
    s3Presigner =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();
    // 생성자 파라미터에 S3Presigner, S3Properties 추가
    return new AiNewsletterClient(
        properties, new ObjectMapper().findAndRegisterModules(), s3Presigner, s3Properties);
  }

  private void sendResponse(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
      throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }
}
