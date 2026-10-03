package com.gachi.be.domain.newsletter.entity.enums;

/**
 * 파이프라인이 PAUSED로 멈춘 사유와, 사유별 버튼 노출 규칙.
 *
 * <p>버튼 규칙은 BE가 계산해서 status API의 retryable/skippable로 내려주고, 프론트는 그 값대로 버튼만 띄운다.
 *
 * <p>UNREADABLE(글자 인식 불가) : 처음 멈추면 [다시 시도]만, 다시 시도까지 실패하면 [건너뛰기]만 OCR_FAILED /
 * TRANSLATION_FAILED(통신 실패) : 처음 멈추면 [다시 시도]만, 다시 시도까지 실패하면 [다시 시도] + [건너뛰기]
 *
 * <p>retryCount는 "현재 사유로 사용자가 다시 시도를 누른 횟수"다.
 */
public enum NewsletterPausedReason {
  OCR_FAILED,
  UNREADABLE,
  TRANSLATION_FAILED;

  /** 사용자가 다시 시도를 몇 번 누른 뒤부터 건너뛰기를 허용할지. */
  private static final int SKIP_ALLOWED_AFTER_RETRY_COUNT = 1;

  /** 인식 불가 페이지에 허용하는 수동 재시도 횟수. */
  private static final int UNREADABLE_MAX_RETRY_COUNT = 1;

  public boolean isRetryable(int retryCount) {
    if (this == UNREADABLE) {
      return retryCount < UNREADABLE_MAX_RETRY_COUNT;
    }
    return true;
  }

  public boolean isSkippable(int retryCount) {
    return retryCount >= SKIP_ALLOWED_AFTER_RETRY_COUNT;
  }

  /** 페이지 실패 상태를 멈춤 사유로 변환한다. 실패 상태가 아니면 null. */
  public static NewsletterPausedReason fromPageStatus(NewsletterPageStatus pageStatus) {
    if (pageStatus == null) {
      return null;
    }
    return switch (pageStatus) {
      case OCR_FAILED -> OCR_FAILED;
      case UNREADABLE -> UNREADABLE;
      case TRANSLATION_FAILED -> TRANSLATION_FAILED;
      default -> null;
    };
  }
}
