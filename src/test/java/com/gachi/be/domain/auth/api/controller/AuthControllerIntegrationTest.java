package com.gachi.be.domain.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.AuthMailService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(
    properties = {
      "app.auth.email.store=memory",
      "app.auth.email.code-ttl-seconds=1",
      "app.auth.email.resend-cooldown-seconds=2",
      "app.auth.email.max-attempts=2",
      "app.auth.email.verified-ttl-seconds=60",
      "app.auth.jwt.secret=test-secret-key-that-is-longer-than-32-bytes"
    })
class AuthControllerIntegrationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private CapturingAuthMailService capturingAuthMailService;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    capturingAuthMailService.clear();
  }

  @Test
  void emailSendCooldown() throws Exception {
    String email = "cooldown@gachi.com";

    sendEmail(email).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("AUTH2003"));

    sendEmail(email)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4291"));
  }

  @Test
  void emailCodeExpired() throws Exception {
    String email = "expired@gachi.com";
    sendEmail(email).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("AUTH2003"));
    String code = capturingAuthMailService.getCode(email);
    assertThat(code).isNotBlank();

    Thread.sleep(1200);

    verifyEmail(email, code)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH4003"));
  }

  @Test
  void emailCodeAttemptExceeded() throws Exception {
    String email = "attempt@gachi.com";
    sendEmail(email).andExpect(status().isOk());

    verifyEmail(email, "111111")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH4002"));

    verifyEmail(email, "222222")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("AUTH4292"));
  }

  @Test
  void signupLoginReissueAndRotation() throws Exception {
    String email = "auth-flow@gachi.com";
    sendEmail(email).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("AUTH2003"));
    String code = capturingAuthMailService.getCode(email);
    assertThat(code).isNotBlank();

    verifyEmail(email, code)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2004"));

    signup(
            """
            {
              "name":"parent1",
              "email":"auth-flow@gachi.com",
              "loginId":"parent_login_1",
              "password":"password123",
              "passwordConfirm":"password123",
              "phoneNumber":"01012345678",
              "consentAgreed":true
            }
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("AUTH2011"));

    sendEmail(email)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4091"));

    MvcResult loginWithoutRemember =
        login(
                """
                {
                  "loginId":"parent_login_1",
                  "password":"password123",
                  "rememberMe":false
                }
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AUTH2001"))
            .andExpect(jsonPath("$.result.rememberMe").value(false))
            .andReturn();

    MvcResult loginWithRemember =
        login(
                """
                {
                  "loginId":"parent_login_1",
                  "password":"password123",
                  "rememberMe":true
                }
                """)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.rememberMe").value(true))
            .andReturn();

    JsonNode noRememberNode = readBody(loginWithoutRemember);
    JsonNode rememberNode = readBody(loginWithRemember);
    OffsetDateTime noRememberExpiresAt =
        OffsetDateTime.parse(noRememberNode.path("result").path("refreshTokenExpiresAt").asText());
    OffsetDateTime rememberExpiresAt =
        OffsetDateTime.parse(rememberNode.path("result").path("refreshTokenExpiresAt").asText());
    long daysBetween = ChronoUnit.DAYS.between(noRememberExpiresAt, rememberExpiresAt);
    assertThat(daysBetween).isGreaterThanOrEqualTo(20);

    String oldRefreshToken = rememberNode.path("result").path("refreshToken").asText();
    MvcResult reissueResult =
        reissue(oldRefreshToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AUTH2002"))
            .andReturn();

    String rotatedRefreshToken =
        readBody(reissueResult).path("result").path("refreshToken").asText();
    assertThat(rotatedRefreshToken).isNotBlank();
    assertThat(rotatedRefreshToken).isNotEqualTo(oldRefreshToken);

    reissue(oldRefreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4014"));
  }

  @Test
  void signupDuplicateChecksBeforeEmailVerified() throws Exception {
    String registeredEmail = "priority-base@gachi.com";
    String registeredLoginId = "priority_login";
    String registeredPhoneNumber = "01099998888";

    sendEmail(registeredEmail).andExpect(status().isOk());
    String code = capturingAuthMailService.getCode(registeredEmail);
    assertThat(code).isNotBlank();
    verifyEmail(registeredEmail, code).andExpect(status().isOk());

    signup(
            signupPayload(
                "parent-priority",
                registeredEmail,
                registeredLoginId,
                "password123",
                "password123",
                registeredPhoneNumber,
                true))
        .andExpect(status().isOk());

    signup(
            signupPayload(
                "dup-email",
                registeredEmail,
                "priority_login_2",
                "password123",
                "password123",
                "01088887777",
                true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4091"));

    signup(
            signupPayload(
                "dup-login-id",
                "priority-login-dup@gachi.com",
                registeredLoginId,
                "password123",
                "password123",
                "01077776666",
                true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4092"));

    signup(
            signupPayload(
                "dup-phone",
                "priority-phone-dup@gachi.com",
                "priority_login_3",
                "password123",
                "password123",
                registeredPhoneNumber,
                true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4093"));
  }

  private org.springframework.test.web.servlet.ResultActions sendEmail(String email)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/email/send")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
  }

  private org.springframework.test.web.servlet.ResultActions verifyEmail(String email, String code)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/email/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))));
  }

  private org.springframework.test.web.servlet.ResultActions signup(String payload)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(payload));
  }

  private String signupPayload(
      String name,
      String email,
      String loginId,
      String password,
      String passwordConfirm,
      String phoneNumber,
      boolean consentAgreed)
      throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "name", name,
            "email", email,
            "loginId", loginId,
            "password", password,
            "passwordConfirm", passwordConfirm,
            "phoneNumber", phoneNumber,
            "consentAgreed", consentAgreed));
  }

  private org.springframework.test.web.servlet.ResultActions login(String payload)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload));
  }

  private org.springframework.test.web.servlet.ResultActions reissue(String refreshToken)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private JsonNode readBody(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @TestConfiguration
  static class MailCaptureConfig {
    @Bean
    @Primary
    CapturingAuthMailService capturingAuthMailService() {
      return new CapturingAuthMailService();
    }
  }

  static class CapturingAuthMailService implements AuthMailService {
    private final Map<String, String> codeByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String email, String code) {
      codeByEmail.put(email, code);
    }

    String getCode(String email) {
      return codeByEmail.get(email);
    }

    void clear() {
      codeByEmail.clear();
    }
  }
}
