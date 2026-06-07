package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.service.EmailVerificationPurpose;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
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
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final long VERIFY_RESULT_EXPIRED = 0L;
  private static final long VERIFY_RESULT_SUCCESS = 1L;
  private static final long VERIFY_RESULT_MISMATCH = 2L;
  private static final long VERIFY_RESULT_ATTEMPT_EXCEEDED = 3L;
  private static final RedisScript<Long> VERIFY_CODE_SCRIPT =
      new DefaultRedisScript<>(
          """
          local code = redis.call('GET', KEYS[1])
          if not code then
            return 0
          end
          local attempts = tonumber(redis.call('GET', KEYS[2]) or '0')
          if attempts >= tonumber(ARGV[2]) then
            return 3
          end
          if code == ARGV[1] then
            redis.call('SET', KEYS[3], '1', 'EX', ARGV[3])
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
          end
          attempts = redis.call('INCR', KEYS[2])
          local ttl = redis.call('TTL', KEYS[1])
          if ttl > 0 then
            redis.call('EXPIRE', KEYS[2], ttl)
          end
          if attempts >= tonumber(ARGV[2]) then
            return 3
          end
          return 2
          """,
          Long.class);

  private final StringRedisTemplate redisTemplate;
  private final AuthProperties authProperties;

  @Override
  public String issueCode(String email, EmailVerificationPurpose purpose) {
    String normalizedEmail = normalizeEmail(email);
    String cooldownKey = cooldownKey(normalizedEmail, purpose);
    Duration cooldownTtl = Duration.ofSeconds(authProperties.getEmail().getResendCooldownSeconds());
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", cooldownTtl);
    if (!Boolean.TRUE.equals(acquired)) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_SEND_COOLDOWN);
    }

    String code = generateCode();
    Duration codeTtl = Duration.ofSeconds(authProperties.getEmail().getCodeTtlSeconds());

    redisTemplate.opsForValue().set(codeKey(normalizedEmail, purpose), code, codeTtl);
    redisTemplate.opsForValue().set(attemptKey(normalizedEmail, purpose), "0", codeTtl);
    return code;
  }

  @Override
  public void rollbackIssuedCode(String email, EmailVerificationPurpose purpose) {
    String normalizedEmail = normalizeEmail(email);
    redisTemplate.delete(codeKey(normalizedEmail, purpose));
    redisTemplate.delete(attemptKey(normalizedEmail, purpose));
    redisTemplate.delete(cooldownKey(normalizedEmail, purpose));
  }

  @Override
  public void verifyCode(String email, String code, EmailVerificationPurpose purpose) {
    String normalizedEmail = normalizeEmail(email);
    // 검증/시도횟수 증가/성공 소모를 한 번에 처리해 병렬 요청 우회를 방지한다.
    Long result =
        redisTemplate.execute(
            VERIFY_CODE_SCRIPT,
            List.of(
                codeKey(normalizedEmail, purpose),
                attemptKey(normalizedEmail, purpose),
                verifiedKey(normalizedEmail, purpose)),
            code,
            String.valueOf(authProperties.getEmail().getMaxAttempts()),
            String.valueOf(authProperties.getEmail().getVerifiedTtlSeconds()));

    long verifyResult = result == null ? VERIFY_RESULT_EXPIRED : result;
    if (verifyResult == VERIFY_RESULT_EXPIRED) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_EXPIRED);
    }
    if (verifyResult == VERIFY_RESULT_ATTEMPT_EXCEEDED) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED);
    }
    if (verifyResult == VERIFY_RESULT_MISMATCH) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_MISMATCH);
    }
    if (verifyResult != VERIFY_RESULT_SUCCESS) {
      throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
    }
  }

  @Override
  public boolean isEmailVerified(String email, EmailVerificationPurpose purpose) {
    return StringUtils.hasText(
        redisTemplate.opsForValue().get(verifiedKey(normalizeEmail(email), purpose)));
  }

  @Override
  public void consumeVerifiedEmail(String email, EmailVerificationPurpose purpose) {
    redisTemplate.delete(verifiedKey(normalizeEmail(email), purpose));
  }

  private String generateCode() {
    // 인증코드 예측 가능성을 낮추기 위해 보안 난수를 사용한다.
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String codeKey(String email, EmailVerificationPurpose purpose) {
    return purposePrefix(purpose) + "code:" + email;
  }

  private String attemptKey(String email, EmailVerificationPurpose purpose) {
    return purposePrefix(purpose) + "attempt:" + email;
  }

  private String cooldownKey(String email, EmailVerificationPurpose purpose) {
    return purposePrefix(purpose) + "cooldown:" + email;
  }

  private String verifiedKey(String email, EmailVerificationPurpose purpose) {
    return purposePrefix(purpose) + "verified:" + email;
  }

  private String purposePrefix(EmailVerificationPurpose purpose) {
    if (purpose == EmailVerificationPurpose.SIGNUP) {
      return KEY_PREFIX;
    }
    return KEY_PREFIX + purpose.keySegment() + ":";
  }
}
