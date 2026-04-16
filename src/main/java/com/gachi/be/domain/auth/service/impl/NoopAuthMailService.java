package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.service.AuthMailService;
import lombok.extern.slf4j.Slf4j;

/** SMTP 미설정 환경에서 메일 미발송 사실과 마스킹된 이메일만 로그로 남긴다. */
@Slf4j
public class NoopAuthMailService implements AuthMailService {
  @Override
  public void sendVerificationCode(String email, String code) {
    log.info("Mail sender is not configured. email={}", maskEmail(email));
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
}
