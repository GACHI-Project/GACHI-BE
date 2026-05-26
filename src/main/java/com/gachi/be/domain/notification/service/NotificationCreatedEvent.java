package com.gachi.be.domain.notification.service;

/** 알림 보관함 저장 커밋 이후 외부 푸시 발송을 시작하기 위한 도메인 이벤트. */
public record NotificationCreatedEvent(Long notificationId, Long userId) {}
