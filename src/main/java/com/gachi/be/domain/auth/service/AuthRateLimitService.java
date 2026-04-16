package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.AuthProperties.Policy;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 인증 엔드포인트별 고정 윈도우 Rate Limit 검증을 제공한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthRateLimitService {
  private static final String EMAIL_SEND_SCOPE = "email-send";
  private static final String LOGIN_SCOPE = "login";
  private static final RedisScript<List<Long>> FIXED_WINDOW_RATE_LIMIT_SCRIPT =
      createFixedWindowRateLimitScript();

  private final StringRedisTemplate redisTemplate;
  private final AuthProperties authProperties;

  /**
   * 이메일 인증코드 발송 엔드포인트 호출 한도를 점검한다.
   *
   * <p>식별자는 IP + normalized email의 HMAC-SHA256 해시 조합을 사용한다.
   */
  public void checkEmailSendRateLimit(String clientIp, String email) {
    if (!isRateLimitEnabled()) {
      return;
    }
    String normalizedEmail = normalizeEmail(email);
    String hashedEmail = hmacSha256Hex(normalizedEmail);
    String key = buildKey(EMAIL_SEND_SCOPE, normalizeIp(clientIp) + ":" + hashedEmail);
    enforceOrThrow(
        key, authProperties.getRateLimit().getEmailSend(), ErrorCode.AUTH_EMAIL_SEND_RATE_LIMITED);
  }

  /** 로그인 엔드포인트 호출 한도를 점검한다. */
  public void checkLoginRateLimit(String clientIp) {
    if (!isRateLimitEnabled()) {
      return;
    }
    String key = buildKey(LOGIN_SCOPE, normalizeIp(clientIp));
    enforceOrThrow(
        key, authProperties.getRateLimit().getLogin(), ErrorCode.AUTH_LOGIN_RATE_LIMITED);
  }

  private boolean isRateLimitEnabled() {
    return authProperties.getRateLimit() != null && authProperties.getRateLimit().isEnabled();
  }

  private void enforceOrThrow(String key, Policy policy, ErrorCode exceedErrorCode) {
    try {
      List<Long> result =
          redisTemplate.execute(
              FIXED_WINDOW_RATE_LIMIT_SCRIPT,
              List.of(key),
              String.valueOf(policy.getLimit()),
              String.valueOf(policy.getWindowSeconds()));

      if (result == null || result.isEmpty()) {
        log.warn(
            "Rate limit script returned empty result. errorCode={}", exceedErrorCode.getCode());
        return;
      }

      long allowed = toLong(result.get(0));
      if (allowed == 0L) {
        throw new BusinessException(exceedErrorCode);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (DataAccessException e) {
      // 인증 가용성을 위해 Redis 일시 장애 시 요청을 열어두고, 장애 감지는 로그로 빠르게 대응한다.
      log.warn(
          "Rate limit backend unavailable. errorCode={}, exceptionType={}",
          exceedErrorCode.getCode(),
          e.getClass().getSimpleName(),
          e);
    }
  }

  @SuppressWarnings("unchecked")
  private static RedisScript<List<Long>> createFixedWindowRateLimitScript() {
    return (RedisScript<List<Long>>)
        (RedisScript<?>)
            new DefaultRedisScript<>(
                """
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                end
                local ttl = redis.call('TTL', KEYS[1])
                if ttl < 0 then
                  redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
                  ttl = tonumber(ARGV[2])
                end
                local allowed = 0
                if current <= tonumber(ARGV[1]) then
                  allowed = 1
                end
                return {allowed, current, ttl}
                """,
                List.class);
  }

  private String buildKey(String scope, String subject) {
    String prefix = authProperties.getRateLimit().getKeyPrefix();
    return prefix + scope + ":" + subject;
  }

  private String normalizeEmail(String email) {
    return normalizeText(email).toLowerCase(Locale.ROOT);
  }

  private String normalizeIp(String clientIp) {
    String normalizedIp = normalizeText(clientIp);
    return StringUtils.hasText(normalizedIp) ? normalizedIp : "unknown";
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }

  private String hmacSha256Hex(String plainText) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      byte[] secret =
          authProperties.getRateLimit().getEmailHmacSecret().getBytes(StandardCharsets.UTF_8);
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to initialize HMAC-SHA256.", e);
    }
  }

  private long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }
}
