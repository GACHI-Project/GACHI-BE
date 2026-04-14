package com.gachi.be.domain.child.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
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
class ChildControllerIntegrationTest {
  private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(1000);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void createAndGetMyChildrenByCreatedAtAsc() throws Exception {
    User parentA = createActiveUser("parent_a");
    User parentB = createActiveUser("parent_b");
    String parentAToken = issueBearerToken(parentA);
    String parentBToken = issueBearerToken(parentB);

    postChild(
            parentAToken,
            Map.of(
                "name", "민수",
                "schoolName", "가치초등학교",
                "grade", 1,
                "colorCode", "#FF5A5A"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("CHILD2011"))
        .andExpect(jsonPath("$.result.grade").value(1));

    postChild(
            parentAToken,
            Map.of(
                "name", "민수",
                "schoolName", "가치초등학교",
                "grade", 6,
                "colorCode", "#00AAFF"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.result.grade").value(6));

    postChild(
            parentBToken,
            Map.of(
                "name", "지우",
                "schoolName", "다른초등학교",
                "grade", 3,
                "colorCode", "#22CC88"))
        .andExpect(status().isCreated());

    MvcResult parentAChildren =
        mockMvc
            .perform(get("/api/v1/children").header("Authorization", parentAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CHILD2001"))
            .andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[0].name").value("민수"))
            .andExpect(jsonPath("$.result[1].name").value("민수"))
            .andReturn();

    JsonNode parentAResult = readBody(parentAChildren).path("result");
    long firstId = parentAResult.path(0).path("id").asLong();
    long secondId = parentAResult.path(1).path("id").asLong();
    assertThat(firstId).isLessThan(secondId);

    mockMvc
        .perform(get("/api/v1/children").header("Authorization", parentBToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.length()").value(1))
        .andExpect(jsonPath("$.result[0].name").value("지우"));
  }

  @Test
  void createChildFailsWhenAuthorizationMissing() throws Exception {
    postChild(
            null,
            Map.of(
                "name", "민수",
                "schoolName", "가치초등학교",
                "grade", 2,
                "colorCode", "#FF5A5A"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4015"));
  }

  @Test
  void createChildFailsWhenTokenInvalid() throws Exception {
    postChild(
            "Bearer invalid-token",
            Map.of(
                "name", "민수",
                "schoolName", "가치초등학교",
                "grade", 2,
                "colorCode", "#FF5A5A"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH4016"));
  }

  @Test
  void createChildFailsWhenValidationRuleViolated() throws Exception {
    User parent = createActiveUser("validation_parent");
    String token = issueBearerToken(parent);

    postChild(
            token,
            Map.of(
                "name", "민수",
                "schoolName", "",
                "grade", 0,
                "colorCode", "FF5A5A"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON4001"))
        .andExpect(jsonPath("$.result.schoolName").exists())
        .andExpect(jsonPath("$.result.grade").exists())
        .andExpect(jsonPath("$.result.colorCode").exists());

    postChild(
            token,
            Map.of(
                "name", "민수",
                "schoolName", "가치초등학교",
                "grade", 7,
                "colorCode", "#00AAFF"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("COMMON4001"))
        .andExpect(jsonPath("$.result.grade").exists());
  }

  private org.springframework.test.web.servlet.ResultActions postChild(
      String authorization, Map<String, Object> body) throws Exception {
    var builder =
        post("/api/v1/children")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body));
    if (authorization != null) {
      builder.header("Authorization", authorization);
    }
    return mockMvc.perform(builder);
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
            .name("학부모-" + postfix)
            .email(postfix + "@gachi.com")
            .loginId("login_" + postfix)
            .passwordHash("encoded-password")
            .phoneNumber("0101234" + String.format("%04d", PHONE_SEQUENCE.getAndIncrement()))
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion("2026-04-v1")
            .passwordUpdatedAt(now)
            .build());
  }
}
