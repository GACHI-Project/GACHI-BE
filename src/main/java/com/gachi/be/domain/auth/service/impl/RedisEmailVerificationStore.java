package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis에 인증코드/시도횟수/인증완료 상태를 저장한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.auth.email",
    name = "store",
    havingValue = "redis",
    matchIfMissing = true)
public class RedisEmailVerificationStore implements EmailVerificationStore {
  private static final String KEY_PREFIX = "auth:email:";

  private final StringRedisTemplate redisTemplate;
  private final AuthProperties authProperties;

  @Override
  public String issueCode(String email) {
    String normalizedEmail = normalizeEmail(email);
    String cooldownKey = cooldownKey(normalizedEmail);
    if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_SEND_COOLDOWN);
    }

    String code = generateCode();
    Duration codeTtl = Duration.ofSeconds(authProperties.getEmail().getCodeTtlSeconds());
    Duration cooldownTtl = Duration.ofSeconds(authProperties.getEmail().getResendCooldownSeconds());

    redisTemplate.opsForValue().set(codeKey(normalizedEmail), code, codeTtl);
    redisTemplate.opsForValue().set(attemptKey(normalizedEmail), "0", codeTtl);
    redisTemplate.opsForValue().set(cooldownKey, "1", cooldownTtl);
    return code;
  }

  @Override
  public void rollbackIssuedCode(String email) {
    String normalizedEmail = normalizeEmail(email);
    redisTemplate.delete(codeKey(normalizedEmail));
    redisTemplate.delete(attemptKey(normalizedEmail));
  }

  @Override
  public void verifyCode(String email, String code) {
    String normalizedEmail = normalizeEmail(email);
    String storedCode = redisTemplate.opsForValue().get(codeKey(normalizedEmail));
    if (!StringUtils.hasText(storedCode)) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_EXPIRED);
    }

    int attempts = parseAttempts(redisTemplate.opsForValue().get(attemptKey(normalizedEmail)));
    if (attempts >= authProperties.getEmail().getMaxAttempts()) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED);
    }

    if (!storedCode.equals(code)) {
      long ttl = redisTemplate.getExpire(codeKey(normalizedEmail), TimeUnit.SECONDS);
      Long nextAttempts = redisTemplate.opsForValue().increment(attemptKey(normalizedEmail));
      if (ttl > 0) {
        redisTemplate.expire(attemptKey(normalizedEmail), Duration.ofSeconds(ttl));
      }
      if (nextAttempts != null && nextAttempts >= authProperties.getEmail().getMaxAttempts()) {
        throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED);
      }
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_MISMATCH);
    }

    redisTemplate
        .opsForValue()
        .set(
            verifiedKey(normalizedEmail),
            "1",
            Duration.ofSeconds(authProperties.getEmail().getVerifiedTtlSeconds()));
    redisTemplate.delete(codeKey(normalizedEmail));
    redisTemplate.delete(attemptKey(normalizedEmail));
  }

  @Override
  public boolean isEmailVerified(String email) {
    return StringUtils.hasText(redisTemplate.opsForValue().get(verifiedKey(normalizeEmail(email))));
  }

  @Override
  public void consumeVerifiedEmail(String email) {
    redisTemplate.delete(verifiedKey(normalizeEmail(email)));
  }

  private String generateCode() {
    return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
  }

  private int parseAttempts(String rawValue) {
    if (!StringUtils.hasText(rawValue)) {
      return 0;
    }
    try {
      return Integer.parseInt(rawValue);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  private String codeKey(String email) {
    return KEY_PREFIX + "code:" + email;
  }

  private String attemptKey(String email) {
    return KEY_PREFIX + "attempt:" + email;
  }

  private String cooldownKey(String email) {
    return KEY_PREFIX + "cooldown:" + email;
  }

  private String verifiedKey(String email) {
    return KEY_PREFIX + "verified:" + email;
  }
}
