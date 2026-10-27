package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.GoogleClient;
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
class RedisGoogleLoginStoreTest {
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RedisGoogleLoginStore store;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store = new RedisGoogleLoginStore(redisTemplate, new ObjectMapper());
  }

  @Test
  void signupTokenCanBeConsumedOnlyOnce() throws Exception {
    GoogleClient.GoogleIdentity identity =
        new GoogleClient.GoogleIdentity("google-sub", "user@gachi.com", true, "민주");
    String json = new ObjectMapper().writeValueAsString(identity);
    when(valueOperations.getAndDelete(startsWith("auth:google:signup:")))
        .thenReturn(json)
        .thenReturn(null);

    assertThat(store.consume("signup", "one-time-token")).isEqualTo(identity);
    assertThatThrownBy(() -> store.consume("signup", "one-time-token"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
  }
}
