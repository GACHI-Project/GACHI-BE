package com.gachi.be.domain.auth.service;

/** 이메일 인증 코드가 어떤 사용자 흐름에서 쓰이는지 구분한다. */
public enum EmailVerificationPurpose {
  SIGNUP("signup"),
  FIND_LOGIN_ID("find-login-id"),
  RESET_PASSWORD("reset-password");

  private final String keySegment;

  EmailVerificationPurpose(String keySegment) {
    this.keySegment = keySegment;
  }

  public String keySegment() {
    return keySegment;
  }
}
