package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.service.AuthMailService;
import lombok.extern.slf4j.Slf4j;

/** SMTP 미설정 환경에서 인증 코드를 로그로 대체 출력한다. */
@Slf4j
public class NoopAuthMailService implements AuthMailService {
  @Override
  public void sendVerificationCode(String email, String code) {
    log.info(
        "Mail sender is not configured. email={}, verificationCode={}",
        maskEmail(email),
        maskCode(code));
  }

  private String maskEmail(String email) {
    if (email == null) {
      return "***";
    }
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***";
    }
    return email.substring(0, 2) + "***" + email.substring(atIndex);
  }

  private String maskCode(String code) {
    if (code == null || code.length() < 2) {
      return "******";
    }
    return code.substring(0, 2) + "****";
  }
}
