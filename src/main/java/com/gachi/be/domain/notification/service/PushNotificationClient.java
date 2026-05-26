package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import java.util.Map;

/** 외부 푸시 provider별 발송 구현체가 맞춰야 하는 공통 계약. */
public interface PushNotificationClient {

  String providerName();

  PushSendResult send(
      Notification notification, PushDeviceToken pushDeviceToken, Map<String, Object> payload);
}
