package com.gachi.be.domain.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.NotificationDeliveryLog;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import com.gachi.be.domain.notification.entity.enums.NotificationDeliveryStatus;
import com.gachi.be.domain.notification.entity.enums.PushPlatform;
import com.gachi.be.domain.notification.repository.NotificationDeliveryLogRepository;
import com.gachi.be.domain.notification.repository.NotificationRepository;
import com.gachi.be.domain.notification.repository.PushDeviceTokenRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.config.external.NotificationPushProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/** 알림 생성 이후 사용자 설정과 토큰 상태를 반영해 외부 푸시 발송을 수행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushDispatcher {
  private static final String PROVIDER_EXPO = "expo";
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final NotificationPushProperties properties;
  private final NotificationRepository notificationRepository;
  private final PushDeviceTokenRepository pushDeviceTokenRepository;
  private final NotificationDeliveryLogRepository deliveryLogRepository;
  private final UserRepository userRepository;
  private final PushNotificationClient pushNotificationClient;
  private final ObjectMapper objectMapper;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void dispatch(NotificationCreatedEvent event) {
    Notification notification =
        notificationRepository.findById(event.notificationId()).orElse(null);
    if (notification == null) {
      log.info(
          "[NotificationPush] 알림을 찾을 수 없어 발송을 건너뜁니다. notificationId={}", event.notificationId());
      return;
    }

    User user = userRepository.findById(event.userId()).orElse(null);
    if (user == null) {
      saveSkipped(notification, null, "사용자를 찾을 수 없어 푸시 발송을 건너뜁니다.");
      return;
    }
    if (!user.isNotificationEnabled()) {
      saveSkipped(notification, null, "사용자 알림 수신 설정이 꺼져 있습니다.");
      return;
    }
    if (!properties.isEnabled()) {
      saveSkipped(notification, null, "푸시 발송 설정이 비활성화되어 있습니다.");
      return;
    }
    if (!PROVIDER_EXPO.equalsIgnoreCase(properties.getProvider())) {
      saveSkipped(notification, null, "지원하지 않는 푸시 provider입니다: " + properties.getProvider());
      return;
    }

    List<PushDeviceToken> tokens =
        pushDeviceTokenRepository.findAllByUserIdAndEnabledTrueAndDeletedAtIsNull(event.userId());
    if (tokens.isEmpty()) {
      saveSkipped(notification, null, "활성 푸시 토큰이 없습니다.");
      return;
    }

    Map<String, Object> payload = deserializePayload(notification.getPayloadJson());
    for (PushDeviceToken token : tokens) {
      dispatchToToken(notification, token, payload);
    }
  }

  private void dispatchToToken(
      Notification notification, PushDeviceToken token, Map<String, Object> payload) {
    if (token.getPlatform() != PushPlatform.EXPO) {
      saveSkipped(notification, token, "현재 provider에서 지원하지 않는 토큰 플랫폼입니다: " + token.getPlatform());
      return;
    }

    PushSendResult result = pushNotificationClient.send(notification, token, payload);
    if (result.success()) {
      saveDeliveryLog(
          notification, token, NotificationDeliveryStatus.SENT, result.providerMessageId(), null);
      return;
    }

    if (result.invalidToken()) {
      token.softDelete();
    }
    saveDeliveryLog(
        notification, token, NotificationDeliveryStatus.FAILED, null, result.failureReason());
  }

  private void saveSkipped(Notification notification, PushDeviceToken token, String failureReason) {
    saveDeliveryLog(notification, token, NotificationDeliveryStatus.SKIPPED, null, failureReason);
  }

  private void saveDeliveryLog(
      Notification notification,
      PushDeviceToken token,
      NotificationDeliveryStatus status,
      String providerMessageId,
      String failureReason) {
    deliveryLogRepository.save(
        NotificationDeliveryLog.builder()
            .notification(notification)
            .pushDeviceToken(token)
            .status(status)
            .provider(pushNotificationClient.providerName())
            .providerMessageId(providerMessageId)
            .failureReason(failureReason)
            .build());
  }

  private Map<String, Object> deserializePayload(String payloadJson) {
    if (!StringUtils.hasText(payloadJson)) {
      return Collections.emptyMap();
    }
    try {
      return objectMapper.readValue(payloadJson, PAYLOAD_TYPE);
    } catch (Exception e) {
      return Map.of("raw", payloadJson);
    }
  }
}
