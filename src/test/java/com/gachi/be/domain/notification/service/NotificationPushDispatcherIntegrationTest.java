package com.gachi.be.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import com.gachi.be.domain.notification.entity.enums.NotificationDeliveryStatus;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.notification.entity.enums.PushPlatform;
import com.gachi.be.domain.notification.repository.NotificationDeliveryLogRepository;
import com.gachi.be.domain.notification.repository.PushDeviceTokenRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.notification.push.enabled=true")
class NotificationPushDispatcherIntegrationTest {
  private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(9000);

  @Autowired private UserRepository userRepository;
  @Autowired private NotificationService notificationService;
  @Autowired private PushDeviceTokenRepository pushDeviceTokenRepository;
  @Autowired private NotificationDeliveryLogRepository deliveryLogRepository;
  @Autowired private CapturingPushNotificationClient pushNotificationClient;

  @BeforeEach
  void setUp() {
    pushNotificationClient.reset();
    userRepository.deleteAll();
  }

  @Test
  void dispatchSendsPushAndRecordsDeliveryLog() {
    User user = createActiveUser("push_success");
    registerToken(user, "ExpoPushToken[success]");
    Notification notification = createNotification(user, "push:success");

    var logs =
        deliveryLogRepository.findAllByNotificationIdOrderByAttemptedAtAsc(notification.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    assertThat(logs.get(0).getProvider()).isEqualTo("TEST");
    assertThat(logs.get(0).getProviderMessageId()).isEqualTo("ticket-1");
    assertThat(pushNotificationClient.sendCount).isEqualTo(1);
    assertThat(pushNotificationClient.lastTitle).isEqualTo("title");
    assertThat(pushNotificationClient.lastBody).isEqualTo("body");
  }

  @Test
  void dispatchSkipsWhenUserDisabledNotification() {
    User user = createActiveUser("push_disabled");
    user.updateNotificationEnabled(false);
    userRepository.saveAndFlush(user);
    registerToken(user, "ExpoPushToken[disabled]");
    Notification notification = createNotification(user, "push:disabled");

    var logs =
        deliveryLogRepository.findAllByNotificationIdOrderByAttemptedAtAsc(notification.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.SKIPPED);
    assertThat(logs.get(0).getProvider()).isEqualTo("EXPO");
    assertThat(pushNotificationClient.sendCount).isZero();
  }

  @Test
  void dispatchDisablesInvalidToken() {
    User user = createActiveUser("push_invalid");
    PushDeviceToken token = registerToken(user, "ExpoPushToken[invalid]");
    pushNotificationClient.nextResult = PushSendResult.failed("Expo token is not registered", true);
    Notification notification = createNotification(user, "push:invalid");

    var logs =
        deliveryLogRepository.findAllByNotificationIdOrderByAttemptedAtAsc(notification.getId());
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.FAILED);
    assertThat(pushDeviceTokenRepository.findById(token.getId()))
        .get()
        .extracting("enabled")
        .isEqualTo(false);
  }

  private Notification createNotification(User user, String dedupeKey) {
    return notificationService.createNotification(
        user.getId(),
        new NotificationCreateCommand(
            NotificationType.SYSTEM, "title", "body", Map.of("targetId", 1), dedupeKey));
  }

  private PushDeviceToken registerToken(User user, String token) {
    notificationService.registerPushToken(
        user.getId(),
        new com.gachi.be.domain.notification.dto.request.PushTokenRegisterRequest(
            PushPlatform.EXPO, token, "device-" + user.getId(), "1.0.0"));
    var tokens =
        pushDeviceTokenRepository.findAllByUserIdAndEnabledTrueAndDeletedAtIsNull(user.getId());
    assertThat(tokens).hasSize(1);
    return tokens.get(0);
  }

  private User createActiveUser(String postfix) {
    OffsetDateTime now = OffsetDateTime.now();
    return userRepository.saveAndFlush(
        User.builder()
            .name("parent-" + postfix)
            .email(postfix + "@gachi.com")
            .loginId("login_" + postfix)
            .passwordHash("encoded-password")
            .phoneNumber("0109999" + String.format("%04d", PHONE_SEQUENCE.getAndIncrement()))
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .languageCode("KO")
            .build());
  }

  @TestConfiguration
  static class PushClientConfig {
    @Bean
    @Primary
    CapturingPushNotificationClient capturingPushNotificationClient() {
      return new CapturingPushNotificationClient();
    }
  }

  static class CapturingPushNotificationClient implements PushNotificationClient {
    private PushSendResult nextResult = PushSendResult.sent("ticket-1");
    private int sendCount;
    private String lastTitle;
    private String lastBody;

    @Override
    public String providerName() {
      return "TEST";
    }

    @Override
    public PushSendResult send(
        Notification notification,
        PushDeviceToken pushDeviceToken,
        Map<String, Object> payload,
        String title,
        String body) {
      sendCount++;
      lastTitle = title;
      lastBody = body;
      return nextResult;
    }

    void reset() {
      nextResult = PushSendResult.sent("ticket-1");
      sendCount = 0;
      lastTitle = null;
      lastBody = null;
    }
  }
}
