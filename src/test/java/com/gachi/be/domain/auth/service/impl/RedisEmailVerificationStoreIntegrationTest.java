package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "app.auth.email.store=redis",
      "app.auth.email.code-ttl-seconds=300",
      "app.auth.email.resend-cooldown-seconds=60",
      "app.auth.email.max-attempts=5",
      "app.auth.email.verified-ttl-seconds=60",
      "app.auth.jwt.secret=test-secret-key-that-is-longer-than-32-bytes"
    })
class RedisEmailVerificationStoreIntegrationTest {

  @Container
  static final GenericContainer<?> REDIS_CONTAINER =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  @Autowired private EmailVerificationStore emailVerificationStore;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearRedisKeys() {
    Set<String> keys = redisTemplate.keys("auth:email:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void redisStoreBeanSelected() {
    assertThat(emailVerificationStore).isInstanceOf(RedisEmailVerificationStore.class);
  }

  @Test
  void rollbackRemovesCooldownSoImmediateRetryIsPossible() {
    String email = "redis-integration@gachi.com";

    String firstCode = emailVerificationStore.issueCode(email);
    assertThat(firstCode).hasSize(6);

    assertThatThrownBy(() -> emailVerificationStore.issueCode(email))
        .isInstanceOf(BusinessException.class)
        .extracting(ex -> ((BusinessException) ex).getErrorCode())
        .isEqualTo(ErrorCode.AUTH_EMAIL_SEND_COOLDOWN);

    emailVerificationStore.rollbackIssuedCode(email);

    String secondCode = emailVerificationStore.issueCode(email);
    assertThat(secondCode).hasSize(6);
  }
}
