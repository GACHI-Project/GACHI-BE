package com.gachi.be.domain.notification.entity.enums;

/** 푸시 발송 시도 결과를 추적하기 위한 상태값. */
public enum NotificationDeliveryStatus {
  PENDING,
  SENT,
  FAILED,
  SKIPPED
}
