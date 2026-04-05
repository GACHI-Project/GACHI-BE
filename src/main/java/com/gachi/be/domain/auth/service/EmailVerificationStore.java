package com.gachi.be.domain.auth.service;

/** Redis(또는 대체 저장소)에 이메일 인증 상태를 저장/검증한다. */
public interface EmailVerificationStore {
  String issueCode(String email);

  void rollbackIssuedCode(String email);

  void verifyCode(String email, String code);

  boolean isEmailVerified(String email);

  void consumeVerifiedEmail(String email);
}
