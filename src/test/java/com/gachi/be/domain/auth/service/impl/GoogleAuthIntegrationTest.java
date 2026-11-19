package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.dto.request.GoogleCompleteRequest;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.GoogleCompleteResponse;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.domain.auth.service.GoogleLoginStore;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.user.dto.request.UserWithdrawalRequest;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.domain.user.service.UserProfileService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
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
  @Autowired private SocialAccountDisconnectService disconnectService;
  @Autowired private AuthRefreshTokenRepository authRefreshTokenRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private AuthTokenIssuer authTokenIssuer;
  @Autowired private UserProfileService userProfileService;
  @Autowired private ChildRepository childRepository;

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
            disconnectService,
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

  @Test
  void unlinkRejectsRemovingLastLoginMethod() {
    GoogleLoginStore googleLoginStore = mock(GoogleLoginStore.class);
    GoogleClient.GoogleIdentity identity =
        new GoogleClient.GoogleIdentity(
            "google-integration-unlink", "google-integration-unlink@gachi.com", true, "통합 회원");
    when(googleLoginStore.consume("signup", "unlink-signup-token")).thenReturn(identity);
    GoogleAuthServiceImpl googleAuthService =
        new GoogleAuthServiceImpl(
            authProperties,
            new GoogleAuthProperties(true, List.of("test-client-id"), 1800),
            mock(GoogleClient.class),
            googleLoginStore,
            socialAccountRepository,
            disconnectService,
            userRepository,
            authTokenIssuer);

    googleAuthService.signup(
        new GoogleSignupRequest(
            "unlink-signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
        "integration-test",
        "127.0.0.1");
    User user = userRepository.findByEmail("google-integration-unlink@gachi.com").orElseThrow();

    assertThatThrownBy(() -> googleAuthService.unlink(user.getId()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_LAST_LOGIN_METHOD));

    assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), SocialProvider.GOOGLE))
        .isPresent();
    assertThat(userRepository.findById(user.getId()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(authRefreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(user.getId()))
        .isNotEmpty();
  }

  @Test
  void unlinkOneOfTwoSocialProvidersKeepsUserActive() {
    GoogleLoginStore store = mock(GoogleLoginStore.class);
    GoogleClient.GoogleIdentity identity =
        new GoogleClient.GoogleIdentity("google-dual", "google-dual@gachi.com", true, "통합 회원");
    when(store.consume("signup", "dual-token")).thenReturn(identity);
    GoogleAuthServiceImpl service =
        new GoogleAuthServiceImpl(
            authProperties,
            new GoogleAuthProperties(true, List.of("test-client-id"), 1800),
            mock(GoogleClient.class),
            store,
            socialAccountRepository,
            disconnectService,
            userRepository,
            authTokenIssuer);
    service.signup(new GoogleSignupRequest("dual-token", true, "KO", null, false), null, null);
    User user = userRepository.findByEmail(identity.email()).orElseThrow();
    socialAccountRepository.saveAndFlush(
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-dual")
            .build());

    service.unlink(user.getId());

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), SocialProvider.GOOGLE))
        .isEmpty();
    assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), SocialProvider.KAKAO))
        .isPresent();
  }

  @Test
  void withdrawnGoogleUserCanSignUpAgainWithoutRestoringOldData() {
    GoogleLoginStore store = mock(GoogleLoginStore.class);
    GoogleClient client = mock(GoogleClient.class);
    GoogleClient.GoogleIdentity identity =
        new GoogleClient.GoogleIdentity(
            "google-resignup", "google-resignup@gachi.com", true, "재가입");
    when(store.consume("signup", "first-token")).thenReturn(identity);
    when(store.consume("signup", "second-token")).thenReturn(identity);
    when(client.verify("id-token")).thenReturn(identity);
    GoogleAuthServiceImpl service =
        new GoogleAuthServiceImpl(
            authProperties,
            new GoogleAuthProperties(true, List.of("test-client-id"), 1800),
            client,
            store,
            socialAccountRepository,
            disconnectService,
            userRepository,
            authTokenIssuer);

    service.signup(
        new GoogleSignupRequest("first-token", true, "KO", NotificationPreference.IMPORTANT, false),
        null,
        null);
    User oldUser = userRepository.findByEmail(identity.email()).orElseThrow();
    childRepository.saveAndFlush(
        Child.builder()
            .user(oldUser)
            .name("기존 자녀")
            .schoolName("학교")
            .grade(1)
            .colorCode("#FFFFFF")
            .build());

    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));
    assertThat(service.complete(new GoogleCompleteRequest("id-token", false), null, null).status())
        .isEqualTo(GoogleCompleteResponse.Status.SIGNUP_REQUIRED);

    service.signup(
        new GoogleSignupRequest(
            "second-token", true, "KO", NotificationPreference.IMPORTANT, false),
        null,
        null);
    User newUser = userRepository.findByEmail(identity.email()).orElseThrow();
    assertThat(newUser.getId()).isNotEqualTo(oldUser.getId());
    assertThat(childRepository.countByUserIdAndDeletedAtIsNull(oldUser.getId())).isEqualTo(1);
    assertThat(childRepository.countByUserIdAndDeletedAtIsNull(newUser.getId())).isZero();
  }
}
