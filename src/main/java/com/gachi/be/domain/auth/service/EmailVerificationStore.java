package com.gachi.be.domain.auth.service;

/** 이메일 인증 상태 저장소 인터페이스다. */
public interface EmailVerificationStore {

  /** 인증 코드를 발급한다. */
  default String issueCode(String email) {
    return issueCode(email, EmailVerificationPurpose.SIGNUP);
  }

  /** 인증 목적별로 분리된 인증 코드를 발급한다. */
  String issueCode(String email, EmailVerificationPurpose purpose);

  /** 메일 발송 실패 시 발급된 인증 정보를 되돌린다. */
  default void rollbackIssuedCode(String email) {
    rollbackIssuedCode(email, EmailVerificationPurpose.SIGNUP);
  }

  /** 메일 발송 실패 시 인증 목적별로 발급된 인증 정보를 되돌린다. */
  void rollbackIssuedCode(String email, EmailVerificationPurpose purpose);

  /** 입력된 인증 코드를 검증한다. */
  default void verifyCode(String email, String code) {
    verifyCode(email, code, EmailVerificationPurpose.SIGNUP);
  }

  /** 인증 목적별로 분리된 인증 코드를 검증한다. */
  void verifyCode(String email, String code, EmailVerificationPurpose purpose);

  /** 이메일 인증 완료 여부를 조회한다. */
  boolean isEmailVerified(String email);

  /** 회원가입 완료 시 인증 완료 상태를 소모한다. */
  void consumeVerifiedEmail(String email);
}
