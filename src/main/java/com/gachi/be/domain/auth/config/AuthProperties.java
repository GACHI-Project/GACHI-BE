package com.gachi.be.domain.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 인증 관련 설정값을 관리한다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
  private String consentVersion = "2026-04-v1";
  private Jwt jwt = new Jwt();
  private Email email = new Email();

  @Getter
  @Setter
  public static class Jwt {
    private String issuer = "gachi-be";
    private String secret = "";
    private long accessTokenMinutes = 15;
    private long refreshTokenDays = 7;
    private long refreshTokenRememberDays = 30;
  }

  @Getter
  @Setter
  public static class Email {
    private String store = "redis";
    private long codeTtlSeconds = 300;
    private long resendCooldownSeconds = 60;
    private int maxAttempts = 5;
    private long verifiedTtlSeconds = 1800;
    private String fromAddress = "";
    private String subject = "[GACHI] Email verification code";
  }
}
