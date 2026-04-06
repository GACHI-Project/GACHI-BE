package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 테스트/로컬 환경에서 Redis 없이 동작할 수 있는 메모리 기반 구현체. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.auth.email", name = "store", havingValue = "memory")
public class InMemoryEmailVerificationStore implements EmailVerificationStore {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final Map<String, ExpiringValue<String>> codeStore = new ConcurrentHashMap<>();
  private final Map<String, ExpiringValue<Integer>> attemptStore = new ConcurrentHashMap<>();
  private final Map<String, ExpiringValue<Boolean>> cooldownStore = new ConcurrentHashMap<>();
  private final Map<String, ExpiringValue<Boolean>> verifiedStore = new ConcurrentHashMap<>();
  private final Map<String, Object> emailLocks = new ConcurrentHashMap<>();
  private final AuthProperties authProperties;

  @Override
  public String issueCode(String email) {
    String normalizedEmail = normalizeEmail(email);
    // 메모리 스토어도 동시 요청에서 정책 우회를 막기 위해 이메일 단위로 직렬화한다.
    synchronized (lockFor(normalizedEmail)) {
      if (isValid(cooldownStore.get(normalizedEmail))) {
        throw new BusinessException(ErrorCode.AUTH_EMAIL_SEND_COOLDOWN);
      }

      String code = generateCode();
      OffsetDateTime codeExpiresAt =
          OffsetDateTime.now().plusSeconds(authProperties.getEmail().getCodeTtlSeconds());
      OffsetDateTime cooldownExpiresAt =
          OffsetDateTime.now().plusSeconds(authProperties.getEmail().getResendCooldownSeconds());

      codeStore.put(normalizedEmail, new ExpiringValue<>(code, codeExpiresAt));
      attemptStore.put(normalizedEmail, new ExpiringValue<>(0, codeExpiresAt));
      cooldownStore.put(normalizedEmail, new ExpiringValue<>(Boolean.TRUE, cooldownExpiresAt));
      return code;
    }
  }

  @Override
  public void rollbackIssuedCode(String email) {
    String normalizedEmail = normalizeEmail(email);
    synchronized (lockFor(normalizedEmail)) {
      codeStore.remove(normalizedEmail);
      attemptStore.remove(normalizedEmail);
      cooldownStore.remove(normalizedEmail);
    }
  }

  @Override
  public void verifyCode(String email, String code) {
    String normalizedEmail = normalizeEmail(email);
    synchronized (lockFor(normalizedEmail)) {
      ExpiringValue<String> storedCode = codeStore.get(normalizedEmail);
      if (!isValid(storedCode)) {
        cleanupExpired(normalizedEmail);
        throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_EXPIRED);
      }

      ExpiringValue<Integer> attemptsValue = attemptStore.get(normalizedEmail);
      int attempts = isValid(attemptsValue) ? attemptsValue.value : 0;
      if (attempts >= authProperties.getEmail().getMaxAttempts()) {
        throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED);
      }

      if (!storedCode.value.equals(code)) {
        int nextAttempts = attempts + 1;
        attemptStore.put(normalizedEmail, new ExpiringValue<>(nextAttempts, storedCode.expiresAt));
        if (nextAttempts >= authProperties.getEmail().getMaxAttempts()) {
          throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_ATTEMPT_EXCEEDED);
        }
        throw new BusinessException(ErrorCode.AUTH_EMAIL_CODE_MISMATCH);
      }

      OffsetDateTime verifiedExpiresAt =
          OffsetDateTime.now().plusSeconds(authProperties.getEmail().getVerifiedTtlSeconds());
      verifiedStore.put(normalizedEmail, new ExpiringValue<>(Boolean.TRUE, verifiedExpiresAt));
      codeStore.remove(normalizedEmail);
      attemptStore.remove(normalizedEmail);
    }
  }

  @Override
  public boolean isEmailVerified(String email) {
    String normalizedEmail = normalizeEmail(email);
    ExpiringValue<Boolean> verified = verifiedStore.get(normalizedEmail);
    if (!isValid(verified)) {
      verifiedStore.remove(normalizedEmail);
      return false;
    }
    return Boolean.TRUE.equals(verified.value);
  }

  @Override
  public void consumeVerifiedEmail(String email) {
    verifiedStore.remove(normalizeEmail(email));
  }

  private void cleanupExpired(String email) {
    codeStore.remove(email);
    attemptStore.remove(email);
  }

  private boolean isValid(ExpiringValue<?> value) {
    return value != null && value.expiresAt.isAfter(OffsetDateTime.now());
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String generateCode() {
    // 인증코드 예측 가능성을 낮추기 위해 보안 난수를 사용한다.
    return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
  }

  private Object lockFor(String normalizedEmail) {
    return emailLocks.computeIfAbsent(normalizedEmail, ignored -> new Object());
  }

  private static class ExpiringValue<T> {
    private final T value;
    private final OffsetDateTime expiresAt;

    private ExpiringValue(T value, OffsetDateTime expiresAt) {
      this.value = value;
      this.expiresAt = expiresAt;
    }
  }
}
