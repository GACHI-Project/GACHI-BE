package com.gachi.be.domain.notification.service;

import com.gachi.be.domain.notification.entity.enums.NotificationDeliveryStatus;

/** 외부 푸시 제공자 발송 결과를 저장할 때 사용하는 입력값. */
public record NotificationDeliveryResultCommand(
    Long notificationId,
    Long pushDeviceTokenId,
    NotificationDeliveryStatus status,
    String providerMessageId,
    String failureReason) {}
