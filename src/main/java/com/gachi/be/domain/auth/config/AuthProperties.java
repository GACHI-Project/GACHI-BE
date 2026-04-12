package com.gachi.be.domain.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 인증/인가 정책 설정값을 불변 객체로 바인딩한다. */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    @DefaultValue("2026-04-v1") String consentVersion,
    @Valid Jwt jwt,
    @Valid Email email,
    @Valid RateLimit rateLimit) {

  public AuthProperties(
      String consentVersion,
      @DefaultValue Jwt jwt,
      @DefaultValue Email email,
      @DefaultValue RateLimit rateLimit) {
    this.consentVersion = consentVersion;
    this.jwt = jwt;
    this.email = email;
    this.rateLimit = rateLimit;
  }

  public String getConsentVersion() {
    return consentVersion;
  }

  public Jwt getJwt() {
    return jwt;
  }

  public Email getEmail() {
    return email;
  }

  public RateLimit getRateLimit() {
    return rateLimit;
  }

  public record Jwt(
      @DefaultValue("gachi-be") String issuer,
      @NotBlank(message = "JWT secret must not be blank.") String secret,
      @DefaultValue("15") @Min(1) long accessTokenMinutes,
      @DefaultValue("7") @Min(1) long refreshTokenDays,
      @DefaultValue("30") @Min(1) long refreshTokenRememberDays) {

    public String getIssuer() {
      return issuer;
    }

    public String getSecret() {
      return secret;
    }

    public long getAccessTokenMinutes() {
      return accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
      return refreshTokenDays;
    }

    public long getRefreshTokenRememberDays() {
      return refreshTokenRememberDays;
    }
  }

  public record Email(
      @DefaultValue("redis") String store,
      @DefaultValue("300") @Min(1) long codeTtlSeconds,
      @DefaultValue("60") @Min(0) long resendCooldownSeconds,
      @DefaultValue("5") @Min(1) int maxAttempts,
      @DefaultValue("1800") @Min(1) long verifiedTtlSeconds,
      @DefaultValue("") String fromAddress,
      @DefaultValue("[GACHI] Email verification code") String subject,
      @DefaultValue("false") boolean noopAllowed) {

    public String getStore() {
      return store;
    }

    public long getCodeTtlSeconds() {
      return codeTtlSeconds;
    }

    public long getResendCooldownSeconds() {
      return resendCooldownSeconds;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public long getVerifiedTtlSeconds() {
      return verifiedTtlSeconds;
    }

    public String getFromAddress() {
      return fromAddress;
    }

    public String getSubject() {
      return subject;
    }

    public boolean isNoopAllowed() {
      return noopAllowed;
    }
  }

  public record RateLimit(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("auth:rate-limit:") String keyPrefix,
      @DefaultValue("") String emailHmacSecret,
      @DefaultValue("127.0.0.1,::1,nginx") List<String> trustedProxies,
      @Valid @DefaultValue Policy emailSend,
      @Valid @DefaultValue Policy login) {

    public boolean isEnabled() {
      return enabled;
    }

    public String getKeyPrefix() {
      return keyPrefix;
    }

    public String getEmailHmacSecret() {
      return emailHmacSecret;
    }

    public List<String> getTrustedProxies() {
      return trustedProxies;
    }

    public Policy getEmailSend() {
      return emailSend;
    }

    public Policy getLogin() {
      return login;
    }

    @AssertTrue(
        message =
            "app.auth.rate-limit.email-hmac-secret must not be blank when rate limit is enabled.")
    public boolean isEmailHmacSecretConfigured() {
      return !enabled || (emailHmacSecret != null && !emailHmacSecret.isBlank());
    }
  }

  public record Policy(
      @DefaultValue("5") @Min(1) int limit, @DefaultValue("60") @Min(1) long windowSeconds) {
    public int getLimit() {
      return limit;
    }

    public long getWindowSeconds() {
      return windowSeconds;
    }
  }
}
