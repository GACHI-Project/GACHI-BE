package com.gachi.be.domain.auth.service;

/** 인증코드 메일 발송 어댑터. */
public interface AuthMailService {
  void sendVerificationCode(String email, String code);
}
