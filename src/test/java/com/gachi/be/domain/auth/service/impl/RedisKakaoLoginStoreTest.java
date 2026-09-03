package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisKakaoLoginStoreTest {
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisKakaoLoginStore store;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store = new RedisKakaoLoginStore(redisTemplate, new ObjectMapper());
  }

  @Test
  void stateCanBeConsumedOnlyOnce() {
    when(valueOperations.getAndDelete("auth:kakao:state:state-token"))
        .thenReturn("1")
        .thenReturn(null);

    assertThat(store.consumeState("state-token")).isTrue();
    assertThat(store.consumeState("state-token")).isFalse();
  }

  @Test
  void ticketCanBeConsumedOnlyOnce() throws Exception {
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity("123", "user@gachi.com", "민주", null);
    String json = new ObjectMapper().writeValueAsString(identity);
    when(valueOperations.getAndDelete(startsWith("auth:kakao:ticket:")))
        .thenReturn(json)
        .thenReturn(null);

    assertThat(store.consume("ticket", "one-time-token")).isEqualTo(identity);
    assertThatThrownBy(() -> store.consume("ticket", "one-time-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_KAKAO_TICKET_INVALID));
  }
}
