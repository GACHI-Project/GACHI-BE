package com.gachi.be.domain.auth.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
    properties = {
      "app.auth.email.store=memory",
      "app.auth.email.code-ttl-seconds=300",
      "app.auth.email.resend-cooldown-seconds=0",
      "app.auth.email.max-attempts=5",
      "app.auth.email.verified-ttl-seconds=60",
      "app.auth.rate-limit.enabled=true",
      "app.auth.rate-limit.key-prefix=auth:rate-limit:test:",
      "app.auth.rate-limit.email-hmac-secret=test-rate-limit-hmac-secret",
      "app.auth.rate-limit.email-send.limit=2",
      "app.auth.rate-limit.email-send.window-seconds=1",
      "app.auth.rate-limit.login.limit=2",
      "app.auth.rate-limit.login.window-seconds=1",
      "app.auth.jwt.secret=test-secret-key-that-is-longer-than-32-bytes"
    })
class AuthRateLimitIntegrationTest {

  @Container
  static final GenericContainer<?> REDIS_CONTAINER =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    clearRateLimitKeys();
  }

  @Test
  void emailSendBlocksWhenIpAndEmailLimitExceeded() throws Exception {
    String email = "rate-email@gachi.com";
    String forwardedFor = "198.51.100.10, 10.0.0.1";

    sendEmailWithForwardedFor(email, forwardedFor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2003"));
    sendEmailWithForwardedFor(email, forwardedFor).andExpect(status().isOk());

    sendEmailWithForwardedFor(email, forwardedFor)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4294"));
  }

  @Test
  void emailSendAllowsRetryAfterWindowExpires() throws Exception {
    String email = "rate-email-retry@gachi.com";
    String forwardedFor = "198.51.100.11";

    sendEmailWithForwardedFor(email, forwardedFor).andExpect(status().isOk());
    sendEmailWithForwardedFor(email, forwardedFor).andExpect(status().isOk());
    sendEmailWithForwardedFor(email, forwardedFor)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4294"));

    Thread.sleep(1100);

    sendEmailWithForwardedFor(email, forwardedFor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2003"));
  }

  @Test
  void loginBlocksWhenIpLimitExceeded() throws Exception {
    createActiveUser("ratelimit_login_1", "RateLimit12!", "login-limit-1@gachi.com", "01012345670");
    String realIp = "203.0.113.20";

    loginWithRealIp("ratelimit_login_1", "RateLimit12!", realIp)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2001"));
    loginWithRealIp("ratelimit_login_1", "RateLimit12!", realIp).andExpect(status().isOk());

    loginWithRealIp("ratelimit_login_1", "RateLimit12!", realIp)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4293"));
  }

  @Test
  void loginAllowsRetryAfterWindowExpires() throws Exception {
    createActiveUser("ratelimit_login_2", "RateLimit12!", "login-limit-2@gachi.com", "01012345671");
    String realIp = "203.0.113.21";

    loginWithRealIp("ratelimit_login_2", "RateLimit12!", realIp).andExpect(status().isOk());
    loginWithRealIp("ratelimit_login_2", "RateLimit12!", realIp).andExpect(status().isOk());
    loginWithRealIp("ratelimit_login_2", "RateLimit12!", realIp)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4293"));

    Thread.sleep(1100);

    loginWithRealIp("ratelimit_login_2", "RateLimit12!", realIp)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2001"));
  }

  private ResultActions sendEmailWithForwardedFor(String email, String forwardedFor)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/email/send")
            .header("X-Forwarded-For", forwardedFor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
  }

  private ResultActions loginWithRealIp(String loginId, String password, String realIp)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .header("X-Real-IP", realIp)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("loginId", loginId, "password", password, "rememberMe", false))));
  }

  private void createActiveUser(
      String loginId, String rawPassword, String email, String phoneNumber) {
    OffsetDateTime now = OffsetDateTime.now();
    userRepository.saveAndFlush(
        User.builder()
            .email(email)
            .loginId(loginId)
            .passwordHash(passwordEncoder.encode(rawPassword))
            .name("rate-limit-user")
            .phoneNumber(phoneNumber)
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .build());
  }

  private void clearRateLimitKeys() {
    Set<String> keys = redisTemplate.keys("auth:rate-limit:test:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }
}
