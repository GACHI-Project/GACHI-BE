package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.service.AuthMailService;
import lombok.extern.slf4j.Slf4j;

/** SMTP 미설정 환경에서 인증 코드를 로그로 대체 출력한다. */
@Slf4j
public class NoopAuthMailService implements AuthMailService {
  @Override
  public void sendVerificationCode(String email, String code) {
    log.info("Mail sender is not configured. email={}, verificationCode={}", email, code);
  }
}
