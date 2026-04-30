package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachi.be.domain.auth.config.AuthProperties;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpAuthMailServiceTest {

  @Test
  void sendVerificationCodeSetsReplyToWhenConfigured() {
    CapturingJavaMailSender javaMailSender = new CapturingJavaMailSender();
    AuthProperties authProperties = createAuthProperties("no-reply@gachi.team.dev");
    SmtpAuthMailService smtpAuthMailService =
        new SmtpAuthMailService(javaMailSender, authProperties);

    smtpAuthMailService.sendVerificationCode("user@example.com", "123456");

    assertThat(javaMailSender.lastMessage).isNotNull();
    assertThat(javaMailSender.lastMessage.getFrom()).isEqualTo("gachi.team.dev@gmail.com");
    assertThat(javaMailSender.lastMessage.getReplyTo()).isEqualTo("no-reply@gachi.team.dev");
    assertThat(javaMailSender.lastMessage.getSubject())
        .isEqualTo("[GACHI] Email verification code");
  }

  @Test
  void sendVerificationCodeSkipsReplyToWhenBlank() {
    CapturingJavaMailSender javaMailSender = new CapturingJavaMailSender();
    AuthProperties authProperties = createAuthProperties("");
    SmtpAuthMailService smtpAuthMailService =
        new SmtpAuthMailService(javaMailSender, authProperties);

    smtpAuthMailService.sendVerificationCode("user@example.com", "654321");

    assertThat(javaMailSender.lastMessage).isNotNull();
    assertThat(javaMailSender.lastMessage.getReplyTo()).isNull();
  }

  private static AuthProperties createAuthProperties(String replyTo) {
    AuthProperties.Jwt jwt =
        new AuthProperties.Jwt(
            "gachi-be", "test-secret-key-that-is-longer-than-32-bytes", 15, 7, 30);
    AuthProperties.Email email =
        new AuthProperties.Email(
            "redis",
            300,
            60,
            5,
            1800,
            "gachi.team.dev@gmail.com",
            replyTo,
            "[GACHI] Email verification code",
            false);
    AuthProperties.Policy emailSendPolicy = new AuthProperties.Policy(3, 300);
    AuthProperties.Policy loginPolicy = new AuthProperties.Policy(5, 60);
    AuthProperties.RateLimit rateLimit =
        new AuthProperties.RateLimit(
            false,
            "auth:rate-limit:",
            "",
            List.of("127.0.0.1", "::1"),
            emailSendPolicy,
            loginPolicy);
    return new AuthProperties("2026-04-v1", jwt, email, rateLimit);
  }

  private static final class CapturingJavaMailSender implements JavaMailSender {
    private SimpleMailMessage lastMessage;

    @Override
    public MimeMessage createMimeMessage() {
      throw new UnsupportedOperationException("현재 테스트에서는 MimeMessage 경로를 사용하지 않습니다.");
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
      throw new UnsupportedOperationException("현재 테스트에서는 MimeMessage 경로를 사용하지 않습니다.");
    }

    @Override
    public void send(MimeMessage mimeMessage) {
      throw new UnsupportedOperationException("현재 테스트에서는 MimeMessage 경로를 사용하지 않습니다.");
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
      throw new UnsupportedOperationException("현재 테스트에서는 MimeMessage 경로를 사용하지 않습니다.");
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) {
      this.lastMessage = simpleMessage;
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) {
      if (simpleMessages == null || simpleMessages.length == 0) {
        this.lastMessage = null;
        return;
      }
      this.lastMessage = simpleMessages[0];
    }
  }
}
