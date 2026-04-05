package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.ExternalApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/** SMTP를 통해 인증 코드를 메일로 발송한다. */
@Slf4j
@RequiredArgsConstructor
public class SmtpAuthMailService implements AuthMailService {
  private final JavaMailSender javaMailSender;
  private final AuthProperties authProperties;

  @Override
  public void sendVerificationCode(String email, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);

    if (StringUtils.hasText(authProperties.getEmail().getFromAddress())) {
      message.setFrom(authProperties.getEmail().getFromAddress());
    }

    message.setSubject(authProperties.getEmail().getSubject());
    message.setText(
        String.format(
            "Verification code: %s%nExpires in: %d seconds.",
            code, authProperties.getEmail().getCodeTtlSeconds()));

    try {
      javaMailSender.send(message);
    } catch (MailException e) {
      log.error("Verification email send failed. email={}", email, e);
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to send verification email.");
    }
  }
}
