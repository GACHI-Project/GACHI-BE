package com.gachi.be.domain.user.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
      "app.auth.email.code-ttl-seconds=60",
      "app.auth.email.resend-cooldown-seconds=2",
      "app.auth.email.max-attempts=2",
      "app.auth.email.verified-ttl-seconds=60",
      "app.auth.rate-limit.enabled=false",
      "app.auth.jwt.secret=test-secret-key-that-is-longer-than-32-bytes"
    })
class UserControllerIntegrationTest {
  private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CapturingAuthMailService capturingAuthMailService;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    capturingAuthMailService.clear();
  }

  @Test
  void updateProfileChangesNameAndPhoneNumber() throws Exception {
    createUser("profile_user", "profile@gachi.com", "01010001000", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("profile_user", "Policy12!");

    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "새 이름", "phoneNumber", "010-2222-3333"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER2003"))
        .andExpect(jsonPath("$.result.name").value("새 이름"))
        .andExpect(jsonPath("$.result.phoneNumber").value("01022223333"));

    User updatedUser = userRepository.findByLoginId("profile_user").orElseThrow();
    assertThat(updatedUser.getName()).isEqualTo("새 이름");
    assertThat(updatedUser.getPhoneNumber()).isEqualTo("01022223333");
  }

  @Test
  void emailChangeCompletesAndRevokesRefreshToken() throws Exception {
    String loginId = "email_change_user";
    createUser(loginId, "email-change-old@gachi.com", "01010001001", UserStatus.ACTIVE);
    JsonNode loginBody = login(loginId, "Policy12!");
    String accessToken = loginBody.path("result").path("accessToken").asText();
    String refreshToken = loginBody.path("result").path("refreshToken").asText();
    String newEmail = "email-change-new@gachi.com";

    sendEmailChangeCode(accessToken, newEmail, "Policy12!")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER2004"));
    String code = capturingAuthMailService.getCode(newEmail);
    assertThat(code).isNotBlank();

    verifyEmailChangeCode(accessToken, newEmail, code)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER2005"));

    changeEmail(accessToken, newEmail)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER2006"))
        .andExpect(jsonPath("$.result.email").value(newEmail));

    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", bearer(accessToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.email").value(newEmail));

    reissue(refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4014"));
  }

  @Test
  void emailChangeSendRejectsWrongCurrentPassword() throws Exception {
    createUser("email_wrong_pw", "email-wrong-pw@gachi.com", "01010001002", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("email_wrong_pw", "Policy12!");

    sendEmailChangeCode(accessToken, "email-wrong-pw-new@gachi.com", "Wrong12!")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4011"));
  }

  @Test
  void emailChangeRejectsDuplicateEmail() throws Exception {
    createUser("email_dup_owner", "email-dup-owner@gachi.com", "01010001003", UserStatus.ACTIVE);
    createUser("email_dup_user", "email-dup-user@gachi.com", "01010001004", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("email_dup_user", "Policy12!");

    sendEmailChangeCode(accessToken, "email-dup-owner@gachi.com", "Policy12!")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4091"));
  }

  @Test
  void emailChangeRejectsWhenEmailCodeIsNotVerified() throws Exception {
    createUser(
        "email_not_verified", "email-not-verified@gachi.com", "01010001005", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("email_not_verified", "Policy12!");

    changeEmail(accessToken, "email-not-verified-new@gachi.com")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH4001"));
  }

  @Test
  void profileUpdateRejectsDuplicatePhoneNumber() throws Exception {
    createUser("phone_dup_owner", "phone-dup-owner@gachi.com", "01010001006", UserStatus.ACTIVE);
    createUser("phone_dup_user", "phone-dup-user@gachi.com", "01010001007", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("phone_dup_user", "Policy12!");

    mockMvc
        .perform(
            patch("/api/v1/users/me/profile")
                .header("Authorization", bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("name", "전화 중복", "phoneNumber", "01010001006"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AUTH4093"));
  }

  @Test
  void passwordChangeUpdatesPasswordAndRevokesRefreshToken() throws Exception {
    String loginId = "password_change_user";
    createUser(loginId, "password-change@gachi.com", "01010001008", UserStatus.ACTIVE);
    JsonNode loginBody = login(loginId, "Policy12!");
    String accessToken = loginBody.path("result").path("accessToken").asText();
    String refreshToken = loginBody.path("result").path("refreshToken").asText();

    changePassword(accessToken, "Policy12!", "Changed12ab", "Changed12ab")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("USER2007"));

    reissue(refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4014"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("loginId", loginId, "password", "Policy12!", "rememberMe", false))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4011"));
    login(loginId, "Changed12ab");
  }

  @Test
  void passwordChangeRejectsWrongCurrentPassword() throws Exception {
    createUser(
        "password_wrong_current",
        "password-wrong-current@gachi.com",
        "01010001009",
        UserStatus.ACTIVE);
    String accessToken = loginAccessToken("password_wrong_current", "Policy12!");

    changePassword(accessToken, "Wrong12!", "Changed12ab", "Changed12ab")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4011"));
  }

  @Test
  void passwordChangeRejectsConfirmMismatch() throws Exception {
    createUser(
        "password_mismatch", "password-mismatch@gachi.com", "01010001010", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("password_mismatch", "Policy12!");

    changePassword(accessToken, "Policy12!", "Changed12ab", "Changed34cd")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH4004"));
  }

  @Test
  void passwordChangeRejectsDangerousPasswordStrength() throws Exception {
    createUser(
        "password_dangerous", "password-dangerous@gachi.com", "01010001011", UserStatus.ACTIVE);
    String accessToken = loginAccessToken("password_dangerous", "Policy12!");

    changePassword(accessToken, "Policy12!", "Qa1x2w3e", "Qa1x2w3e")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("AUTH4009"));
  }

  private org.springframework.test.web.servlet.ResultActions sendEmailChangeCode(
      String accessToken, String email, String currentPassword) throws Exception {
    return mockMvc.perform(
        post("/api/v1/users/me/email/send")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of("email", email, "currentPassword", currentPassword))));
  }

  private org.springframework.test.web.servlet.ResultActions verifyEmailChangeCode(
      String accessToken, String email, String code) throws Exception {
    return mockMvc.perform(
        post("/api/v1/users/me/email/verify")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))));
  }

  private org.springframework.test.web.servlet.ResultActions changeEmail(
      String accessToken, String email) throws Exception {
    return mockMvc.perform(
        patch("/api/v1/users/me/email")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("email", email))));
  }

  private org.springframework.test.web.servlet.ResultActions changePassword(
      String accessToken, String currentPassword, String newPassword, String newPasswordConfirm)
      throws Exception {
    return mockMvc.perform(
        patch("/api/v1/users/me/password")
            .header("Authorization", bearer(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    Map.of(
                        "currentPassword",
                        currentPassword,
                        "newPassword",
                        newPassword,
                        "newPasswordConfirm",
                        newPasswordConfirm))));
  }

  private org.springframework.test.web.servlet.ResultActions reissue(String refreshToken)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/reissue")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private String loginAccessToken(String loginId, String password) throws Exception {
    return login(loginId, password).path("result").path("accessToken").asText();
  }

  private JsonNode login(String loginId, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("loginId", loginId, "password", password, "rememberMe", false))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String bearer(String accessToken) {
    return "Bearer " + accessToken;
  }

  private void createUser(String loginId, String email, String phoneNumber, UserStatus status) {
    OffsetDateTime now = OffsetDateTime.now();
    userRepository.saveAndFlush(
        User.builder()
            .email(email)
            .loginId(loginId)
            .passwordHash(passwordEncoder.encode("Policy12!"))
            .name(loginId)
            .phoneNumber(phoneNumber)
            .status(status)
            .languageCode("KO")
            .notificationPreference(NotificationPreference.IMPORTANT)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .passwordChangeRequired(false)
            .build());
  }

  @TestConfiguration
  static class TestMailConfig {
    @Bean
    @Primary
    CapturingAuthMailService capturingAuthMailService() {
      return new CapturingAuthMailService();
    }
  }

  static class CapturingAuthMailService implements AuthMailService {
    private final Map<String, String> codes = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String email, String code) {
      codes.put(email, code);
    }

    String getCode(String email) {
      return codes.get(email);
    }

    void clear() {
      codes.clear();
    }
  }
}
