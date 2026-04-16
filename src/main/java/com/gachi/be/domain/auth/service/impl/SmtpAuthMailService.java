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
    if (StringUtils.hasText(authProperties.getEmail().getReplyTo())) {
      message.setReplyTo(authProperties.getEmail().getReplyTo());
    }

    message.setSubject(authProperties.getEmail().getSubject());
    message.setText(
        String.format(
            "Verification code: %s%nExpires in: %d seconds.",
            code, authProperties.getEmail().getCodeTtlSeconds()));

    try {
      javaMailSender.send(message);
    } catch (MailException e) {
      // 장애 분석에 필요한 정보는 남기되 개인정보는 로그에 그대로 남기지 않는다.
      log.error("Verification email send failed. email={}", maskEmail(email), e);
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to send verification email.");
    }
  }

  private String maskEmail(String email) {
    if (!StringUtils.hasText(email)) {
      return "***";
    }
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***";
    }
    return email.substring(0, 2) + "***" + email.substring(atIndex);
  }
}
