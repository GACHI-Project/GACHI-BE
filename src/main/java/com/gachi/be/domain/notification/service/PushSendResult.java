package com.gachi.be.domain.notification.service;

/** 외부 푸시 provider 발송 결과를 delivery log 정책에 맞게 표현한다. */
public record PushSendResult(
    boolean success, String providerMessageId, String failureReason, boolean invalidToken) {

  public static PushSendResult sent(String providerMessageId) {
    return new PushSendResult(true, providerMessageId, null, false);
  }

  public static PushSendResult failed(String failureReason, boolean invalidToken) {
    return new PushSendResult(false, null, failureReason, invalidToken);
  }
}
