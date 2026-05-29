package com.gachi.be.domain.user.entity.enums;

import com.gachi.be.domain.notification.entity.enums.NotificationLevel;

/** 사용자가 외부 푸시로 받고 싶은 알림 범위를 나타낸다. */
public enum NotificationPreference {
  URGENT_ONLY,
  IMPORTANT,
  ALL,
  OFF;

  public static NotificationPreference defaultValue() {
    return IMPORTANT;
  }

  public static NotificationPreference fromLegacyEnabled(Boolean notificationEnabled) {
    if (notificationEnabled == null) {
      return defaultValue();
    }
    return notificationEnabled ? defaultValue() : OFF;
  }

  public boolean allows(NotificationLevel level) {
    NotificationLevel resolvedLevel = level != null ? level : NotificationLevel.IMPORTANT;
    return switch (this) {
      case ALL -> true;
      case IMPORTANT -> resolvedLevel != NotificationLevel.NORMAL;
      case URGENT_ONLY -> resolvedLevel == NotificationLevel.URGENT;
      case OFF -> false;
    };
  }

  public boolean isPushEnabled() {
    return this != OFF;
  }
}
