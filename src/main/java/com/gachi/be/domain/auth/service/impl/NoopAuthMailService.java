package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.service.AuthMailService;
import lombok.extern.slf4j.Slf4j;

/**
 * SMTP 미설정 환경에서 메일 미발송 사실을 로그로 남기는 noop 구현체.
 *
 * <p><strong>경고:</strong> 이 구현체는 개발 환경 전용입니다. 프로덕션에서 사용하면 인증 흐름이 실제 메일 발송 없이 진행되어 보안 위험이 발생할 수
 * 있습니다.
 *
 * @see com.gachi.be.domain.auth.config.AuthConfig#authMailService
 */
@Slf4j
public class NoopAuthMailService implements AuthMailService {
  @Override
  public void sendVerificationCode(String email, String code) {
    log.warn("Mail sender is not configured. Verification email not sent to: {}", maskEmail(email));
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
