package com.gachi.be.domain.auth.service;

/** 이메일 인증 상태 저장소 인터페이스다. */
public interface EmailVerificationStore {

  /** 인증 코드를 발급한다. */
  String issueCode(String email);

  /** 메일 발송 실패 시 발급된 인증 정보를 되돌린다. */
  void rollbackIssuedCode(String email);

  /** 입력된 인증 코드를 검증한다. */
  void verifyCode(String email, String code);

  /** 이메일 인증 완료 여부를 조회한다. */
  boolean isEmailVerified(String email);

  /** 회원가입 완료 시 인증 완료 상태를 소모한다. */
  void consumeVerifiedEmail(String email);
}
