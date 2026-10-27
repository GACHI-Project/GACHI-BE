package com.gachi.be.domain.auth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.domain.auth.service.GoogleLoginStore;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RedisGoogleLoginStore implements GoogleLoginStore {
  private static final String PREFIX = "auth:google:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public String issue(String purpose, GoogleClient.GoogleIdentity identity, Duration ttl) {
    String token = UUID.randomUUID().toString().replace("-", "");
    try {
      redisTemplate
          .opsForValue()
          .set(key(purpose, token), objectMapper.writeValueAsString(identity), ttl);
      return token;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize Google login identity.", e);
    }
  }

  @Override
  public GoogleClient.GoogleIdentity consume(String purpose, String token) {
    String value =
        StringUtils.hasText(token)
            ? redisTemplate.opsForValue().getAndDelete(key(purpose, token))
            : null;
    if (!StringUtils.hasText(value)) {
      throw invalidToken();
    }
    try {
      return objectMapper.readValue(value, GoogleClient.GoogleIdentity.class);
    } catch (JsonProcessingException e) {
      throw invalidToken();
    }
  }

  private String key(String purpose, String token) {
    return PREFIX + purpose + ":" + token;
  }

  private BusinessException invalidToken() {
    return new BusinessException(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
  }
}
