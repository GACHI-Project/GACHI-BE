package com.gachi.be.domain.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import com.gachi.be.global.config.external.NotificationPushProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Expo Push API로 React Native 앱 푸시 알림을 발송한다. */
@Slf4j
@Component
public class ExpoPushNotificationClient implements PushNotificationClient {
  private static final String PROVIDER_NAME = "EXPO";
  private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

  private final NotificationPushProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public ExpoPushNotificationClient(
      NotificationPushProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PushSendResult send(
      Notification notification, PushDeviceToken pushDeviceToken, Map<String, Object> payload) {
    try {
      String body =
          objectMapper.writeValueAsString(
              new ExpoPushRequest(
                  pushDeviceToken.getToken(),
                  notification.getTitle(),
                  notification.getBody(),
                  "default",
                  payload != null ? payload : Map.of()));

      HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(properties.getExpo().getApiUrl()))
              .header("Accept", "application/json")
              .header("Accept-Encoding", "gzip, deflate")
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (StringUtils.hasText(properties.getExpo().getAccessToken())) {
        requestBuilder.header("Authorization", "Bearer " + properties.getExpo().getAccessToken());
      }

      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return PushSendResult.failed(
            "Expo Push API HTTP " + response.statusCode() + ": " + response.body(), false);
      }
      return parseTicket(response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return PushSendResult.failed("Expo Push API 호출이 인터럽트되었습니다.", false);
    } catch (IOException | IllegalArgumentException e) {
      log.warn("[ExpoPush] 푸시 발송 실패. notificationId={}", notification.getId(), e);
      return PushSendResult.failed("Expo Push API 호출 실패: " + e.getMessage(), false);
    }
  }

  private PushSendResult parseTicket(String responseBody) throws IOException {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode ticket = root.path("data");
    if (ticket.isArray()) {
      ticket = ticket.isEmpty() ? objectMapper.nullNode() : ticket.get(0);
    }
    String status = ticket.path("status").asText("");
    if ("ok".equals(status)) {
      return PushSendResult.sent(ticket.path("id").asText(null));
    }

    String message = ticket.path("message").asText("Expo Push API 발송 실패");
    String error = ticket.path("details").path("error").asText("");
    boolean invalidToken = DEVICE_NOT_REGISTERED.equals(error);
    String failureReason = StringUtils.hasText(error) ? message + " (" + error + ")" : message;
    return PushSendResult.failed(failureReason, invalidToken);
  }

  private record ExpoPushRequest(
      String to, String title, String body, String sound, Map<String, Object> data) {}
}
