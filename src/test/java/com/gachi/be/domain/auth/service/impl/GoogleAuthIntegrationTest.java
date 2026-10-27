package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.domain.auth.service.GoogleLoginStore;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoogleAuthIntegrationTest {
  @Autowired private AuthProperties authProperties;
  @Autowired private UserRepository userRepository;
  @Autowired private SocialAccountRepository socialAccountRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private AuthTokenIssuer authTokenIssuer;

  @Test
  void socialSignupPersistsAccountAndIssuesUsableGachiTokens() {
    GoogleLoginStore googleLoginStore = mock(GoogleLoginStore.class);
    GoogleClient.GoogleIdentity identity =
        new GoogleClient.GoogleIdentity(
            "google-integration-1", "google-integration@gachi.com", true, "통합 회원");
    when(googleLoginStore.consume("signup", "signup-token")).thenReturn(identity);
    GoogleAuthServiceImpl googleAuthService =
        new GoogleAuthServiceImpl(
            authProperties,
            new GoogleAuthProperties(true, List.of("test-client-id"), 1800),
            mock(GoogleClient.class),
            googleLoginStore,
            socialAccountRepository,
            userRepository,
            authTokenIssuer);

    AuthTokenResponse tokens =
        googleAuthService.signup(
            new GoogleSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            "integration-test",
            "127.0.0.1");

    User user = userRepository.findByEmail("google-integration@gachi.com").orElseThrow();
    assertThat(user.getLoginId()).isNull();
    assertThat(user.getPasswordHash()).isNull();
    assertThat(user.getPhoneNumber()).isNull();
    assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), SocialProvider.GOOGLE))
        .isPresent();
    assertThat(jwtTokenProvider.parseAccessToken(tokens.accessToken()).getUserId())
        .isEqualTo(user.getId());
    assertThat(tokens.refreshToken()).isNotBlank();
  }
}
