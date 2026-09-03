package com.gachi.be.domain.auth.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.domain.auth.service.KakaoLoginStore;
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
public class RedisKakaoLoginStore implements KakaoLoginStore {
  private static final String PREFIX = "auth:kakao:";
  private static final String STATE = "state";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  public String issueState(Duration ttl) {
    String state = randomToken();
    redisTemplate.opsForValue().set(key(STATE, state), "1", ttl);
    return state;
  }

  @Override
  public boolean consumeState(String state) {
    return StringUtils.hasText(state)
        && redisTemplate.opsForValue().getAndDelete(key(STATE, state)) != null;
  }

  @Override
  public String issue(String purpose, KakaoClient.KakaoIdentity identity, Duration ttl) {
    String token = randomToken();
    try {
      redisTemplate
          .opsForValue()
          .set(key(purpose, token), objectMapper.writeValueAsString(identity), ttl);
      return token;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize Kakao login identity.", e);
    }
  }

  @Override
  public KakaoClient.KakaoIdentity consume(String purpose, String token) {
    String value =
        StringUtils.hasText(token)
            ? redisTemplate.opsForValue().getAndDelete(key(purpose, token))
            : null;
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_TICKET_INVALID);
    }
    try {
      return objectMapper.readValue(value, KakaoClient.KakaoIdentity.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_TICKET_INVALID);
    }
  }

  private String key(String purpose, String token) {
    return PREFIX + purpose + ":" + token;
  }

  private String randomToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
