package com.gachi.be.domain.notification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.notification.entity.Notification;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.notification.repository.NotificationRepository;
import com.gachi.be.domain.notification.repository.PushDeviceTokenRepository;
import com.gachi.be.domain.notification.service.NotificationCreateCommand;
import com.gachi.be.domain.notification.service.NotificationService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {
  private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(7000);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private NotificationService notificationService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private PushDeviceTokenRepository pushDeviceTokenRepository;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void notificationInboxSupportsCursorUnreadCountAndReadState() throws Exception {
    User user = createActiveUser("notification_parent");
    String token = issueBearerToken(user);

    Notification first =
        notificationService.createNotification(
            user.getId(),
            new NotificationCreateCommand(
                NotificationType.NEWSLETTER_ANALYSIS,
                "analysis complete",
                "first body",
                Map.of("newsletterId", 10L),
                "newsletter:10:completed"));
    Notification duplicated =
        notificationService.createNotification(
            user.getId(),
            new NotificationCreateCommand(
                NotificationType.NEWSLETTER_ANALYSIS,
                "analysis complete again",
                "duplicated body",
                Map.of("newsletterId", 10L),
                "newsletter:10:completed"));
    Notification second =
        notificationService.createNotification(
            user.getId(),
            new NotificationCreateCommand(
                NotificationType.CALENDAR_EVENT,
                "calendar registered",
                "second body",
                Map.of("calendarEventId", 20L),
                "calendar:20:registered"));

    assertThat(duplicated.getId()).isEqualTo(first.getId());
    assertThat(notificationRepository.countByUserIdAndReadAtIsNull(user.getId())).isEqualTo(2);

    MvcResult firstPage =
        mockMvc
            .perform(get("/api/v1/notifications").header("Authorization", token).param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("NOTI2001"))
            .andExpect(jsonPath("$.result.notifications.length()").value(1))
            .andExpect(jsonPath("$.result.notifications[0].id").value(second.getId()))
            .andExpect(jsonPath("$.result.notifications[0].payload.calendarEventId").value(20))
            .andExpect(jsonPath("$.result.hasNext").value(true))
            .andReturn();

    Long nextCursor = readBody(firstPage).path("result").path("nextCursor").asLong();

    mockMvc
        .perform(
            get("/api/v1/notifications")
                .header("Authorization", token)
                .param("cursor", String.valueOf(nextCursor))
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.notifications.length()").value(1))
        .andExpect(jsonPath("$.result.notifications[0].id").value(first.getId()))
        .andExpect(jsonPath("$.result.hasNext").value(false));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTI2002"))
        .andExpect(jsonPath("$.result.unreadCount").value(2));

    mockMvc
        .perform(
            patch("/api/v1/notifications/{notificationId}/read", second.getId())
                .header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.readCount").value(1));

    mockMvc
        .perform(
            patch("/api/v1/notifications/read")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("notificationIds", List.of(first.getId())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.readCount").value(1));

    mockMvc
        .perform(get("/api/v1/notifications/unread-count").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.unreadCount").value(0));
  }

  @Test
  void pushTokenCanBeRegisteredDeletedAndReRegistered() throws Exception {
    User user = createActiveUser("push_token_parent");
    String bearerToken = issueBearerToken(user);
    Map<String, Object> registerBody =
        Map.of(
            "platform", "EXPO",
            "token", "ExpoPushToken[test-token]",
            "deviceId", "device-1",
            "appVersion", "1.0.0");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/notifications/tokens")
                    .header("Authorization", bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("NOTI2004"))
            .andExpect(jsonPath("$.result.platform").value("EXPO"))
            .andExpect(jsonPath("$.result.enabled").value(true))
            .andReturn();

    long tokenId = readBody(created).path("result").path("id").asLong();

    mockMvc
        .perform(
            delete("/api/v1/notifications/tokens")
                .header("Authorization", bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(Map.of("token", "ExpoPushToken[test-token]"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("NOTI2005"));

    assertThat(pushDeviceTokenRepository.findByIdAndUserId(tokenId, user.getId()))
        .get()
        .extracting("enabled")
        .isEqualTo(false);

    mockMvc
        .perform(
            post("/api/v1/notifications/tokens")
                .header("Authorization", bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.id").value(tokenId))
        .andExpect(jsonPath("$.result.enabled").value(true));
  }

  @Test
  void readingOtherUsersNotificationReturnsNotFound() throws Exception {
    User owner = createActiveUser("notification_owner");
    User other = createActiveUser("notification_other");
    String otherToken = issueBearerToken(other);
    Notification notification =
        notificationService.createNotification(
            owner.getId(),
            new NotificationCreateCommand(
                NotificationType.SYSTEM, "system", "body", Map.of(), "system:owner"));

    mockMvc
        .perform(
            patch("/api/v1/notifications/{notificationId}/read", notification.getId())
                .header("Authorization", otherToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTI4041"));
  }

  private JsonNode readBody(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String issueBearerToken(User user) {
    return "Bearer " + jwtTokenProvider.issueAccessToken(user).getToken();
  }

  private User createActiveUser(String postfix) {
    OffsetDateTime now = OffsetDateTime.now();
    return userRepository.saveAndFlush(
        User.builder()
            .name("parent-" + postfix)
            .email(postfix + "@gachi.com")
            .loginId("login_" + postfix)
            .passwordHash("encoded-password")
            .phoneNumber("0107777" + String.format("%04d", PHONE_SEQUENCE.getAndIncrement()))
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .build());
  }
}
