package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.request.KakaoCompleteRequest;
import com.gachi.be.domain.auth.dto.request.KakaoLinkRequest;
import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.KakaoCompleteResponse;
import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.auth.service.KakaoAuthService;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.domain.auth.service.KakaoLoginStore;
import com.gachi.be.domain.user.dto.request.UserWithdrawalRequest;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.domain.user.service.UserProfileService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KakaoAuthIntegrationTest {
  @Autowired private UserRepository userRepository;
  @Autowired private SocialAccountRepository socialAccountRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private AuthTokenIssuer authTokenIssuer;
  @Autowired private AuthRefreshTokenRepository authRefreshTokenRepository;
  @Autowired private KakaoUnlinkOutboxRepository kakaoUnlinkOutboxRepository;
  @Autowired private UserProfileService userProfileService;

  @Test
  void socialSignupPersistsAccountAndIssuesUsableGachiTokens() {
    KakaoLoginStore kakaoLoginStore = mock(KakaoLoginStore.class);
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity(
            "kakao-integration-1", "integration@gachi.com", "통합 회원", null);
    when(kakaoLoginStore.consume("signup", "signup-token")).thenReturn(identity);
    KakaoAuthService kakaoAuthService =
        new KakaoAuthServiceImpl(
            kakaoEnabledAuthProperties(),
            mock(KakaoClient.class),
            kakaoLoginStore,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            kakaoUnlinkOutboxRepository,
            new SocialAccountDisconnectService(
                socialAccountRepository, authRefreshTokenRepository));

    AuthTokenResponse tokens =
        kakaoAuthService.signup(
            new KakaoSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            "integration-test",
            "127.0.0.1");

    User user = userRepository.findByEmail("integration@gachi.com").orElseThrow();
    assertThat(user.getLoginId()).isNull();
    assertThat(user.getPasswordHash()).isNull();
    assertThat(user.getPhoneNumber()).isNull();
    assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), SocialProvider.KAKAO))
        .isPresent();
    assertThat(jwtTokenProvider.parseAccessToken(tokens.accessToken()).getUserId())
        .isEqualTo(user.getId());
    assertThat(tokens.refreshToken()).isNotBlank();
  }

  @Test
  void staleUnlinkEventDoesNotDisconnectReconnectedAccount() {
    User originalUser = userRepository.saveAndFlush(socialUser("original@gachi.com", "기존 회원"));
    SocialAccount originalAccount =
        socialAccountRepository.saveAndFlush(
            SocialAccount.builder()
                .user(originalUser)
                .provider(SocialProvider.KAKAO)
                .providerUserId("kakao-reconnected")
                .build());
    KakaoUnlinkOutbox event =
        kakaoUnlinkOutboxRepository.saveAndFlush(
            KakaoUnlinkOutbox.builder()
                .userId(originalUser.getId())
                .providerUserId("kakao-reconnected")
                .build());

    socialAccountRepository.delete(originalAccount);
    socialAccountRepository.flush();
    User newUser = userRepository.saveAndFlush(socialUser("new-owner@gachi.com", "새 회원"));
    socialAccountRepository.saveAndFlush(
        SocialAccount.builder()
            .user(newUser)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-reconnected")
            .build());

    KakaoClient kakaoClient = mock(KakaoClient.class);
    KakaoUnlinkOutboxProcessor processor =
        new KakaoUnlinkOutboxProcessor(
            kakaoUnlinkOutboxRepository,
            socialAccountRepository,
            userRepository,
            new SocialAccountDisconnectService(socialAccountRepository, authRefreshTokenRepository),
            kakaoClient);

    processor.process(event.getId());

    verify(kakaoClient, never()).unlink(anyString());
    assertThat(
            socialAccountRepository.findByUserIdAndProvider(newUser.getId(), SocialProvider.KAKAO))
        .isPresent();
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isTrue();
  }

  @Test
  void withdrawnGoogleUserCanSignUpWithKakaoUsingSameEmail() {
    User oldUser = userRepository.saveAndFlush(socialUser("cross-provider@gachi.com", "기존 회원"));
    socialAccountRepository.saveAndFlush(
        SocialAccount.builder()
            .user(oldUser)
            .provider(SocialProvider.GOOGLE)
            .providerUserId("google-cross-provider")
            .build());
    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));

    KakaoLoginStore store = mock(KakaoLoginStore.class);
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity(
            "kakao-cross-provider", "cross-provider@gachi.com", "새 회원", null);
    when(store.consume("ticket", "ticket-token")).thenReturn(identity);
    when(store.consume("signup", "signup-token")).thenReturn(identity);
    KakaoAuthServiceImpl service =
        new KakaoAuthServiceImpl(
            kakaoEnabledAuthProperties(),
            mock(KakaoClient.class),
            store,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            kakaoUnlinkOutboxRepository,
            new SocialAccountDisconnectService(
                socialAccountRepository, authRefreshTokenRepository));

    assertThat(
            service.complete(new KakaoCompleteRequest("ticket-token", false), null, null).status())
        .isEqualTo(KakaoCompleteResponse.Status.SIGNUP_REQUIRED);
    AuthTokenResponse tokens =
        service.signup(
            new KakaoSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            null,
            null);

    User newUser = userRepository.findByEmail(identity.email()).orElseThrow();
    assertThat(newUser.getId()).isNotEqualTo(oldUser.getId());
    assertThat(jwtTokenProvider.parseAccessToken(tokens.accessToken()).getUserId())
        .isEqualTo(newUser.getId());
    assertThat(
            socialAccountRepository.findByUserIdAndProvider(oldUser.getId(), SocialProvider.GOOGLE))
        .isEmpty();
    assertThat(
            socialAccountRepository.findByUserIdAndProvider(newUser.getId(), SocialProvider.KAKAO))
        .isPresent();
  }

  @Test
  void withdrawnKakaoUserCanSignUpAgainWithSameProviderUserId() {
    User oldUser = userRepository.saveAndFlush(socialUser("kakao-resignup@gachi.com", "기존 회원"));
    socialAccountRepository.saveAndFlush(
        SocialAccount.builder()
            .user(oldUser)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-resignup")
            .build());
    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));

    KakaoLoginStore store = mock(KakaoLoginStore.class);
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity("kakao-resignup", "kakao-resignup@gachi.com", "새 회원", null);
    when(store.consume("ticket", "ticket-token")).thenReturn(identity);
    when(store.consume("signup", "signup-token")).thenReturn(identity);
    KakaoAuthServiceImpl service =
        new KakaoAuthServiceImpl(
            kakaoEnabledAuthProperties(),
            mock(KakaoClient.class),
            store,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            kakaoUnlinkOutboxRepository,
            new SocialAccountDisconnectService(
                socialAccountRepository, authRefreshTokenRepository));

    assertThat(
            service.complete(new KakaoCompleteRequest("ticket-token", false), null, null).status())
        .isEqualTo(KakaoCompleteResponse.Status.SIGNUP_REQUIRED);
    AuthTokenResponse tokens =
        service.signup(
            new KakaoSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            null,
            null);

    User newUser = userRepository.findByEmail(identity.email()).orElseThrow();
    assertThat(newUser.getId()).isNotEqualTo(oldUser.getId());
    assertThat(jwtTokenProvider.parseAccessToken(tokens.accessToken()).getUserId())
        .isEqualTo(newUser.getId());
  }

  @Test
  void withdrawalPreservesPendingKakaoUnlinkUntilRemoteUnlinkSucceeds() {
    User user = userRepository.saveAndFlush(socialUser("pending-unlink@gachi.com", "기존 회원"));
    SocialAccount account =
        socialAccountRepository.saveAndFlush(
            SocialAccount.builder()
                .user(user)
                .provider(SocialProvider.KAKAO)
                .providerUserId("kakao-pending-unlink")
                .build());
    account.requestDisconnect();
    KakaoUnlinkOutbox event =
        kakaoUnlinkOutboxRepository.saveAndFlush(
            KakaoUnlinkOutbox.builder()
                .userId(user.getId())
                .providerUserId("kakao-pending-unlink")
                .build());

    userProfileService.withdraw(user, new UserWithdrawalRequest(null));
    assertThat(socialAccountRepository.findById(account.getId())).isPresent();
    assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);

    KakaoClient client = mock(KakaoClient.class);
    new KakaoUnlinkOutboxProcessor(
            kakaoUnlinkOutboxRepository,
            socialAccountRepository,
            userRepository,
            new SocialAccountDisconnectService(socialAccountRepository, authRefreshTokenRepository),
            client)
        .process(event.getId());

    verify(client).unlink("kakao-pending-unlink");
    assertThat(socialAccountRepository.findById(account.getId())).isEmpty();
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isTrue();
  }

  @Test
  void resignupCancelsPendingUnlinkWithoutDisconnectingNewKakaoAccount() {
    User oldUser = userRepository.saveAndFlush(socialUser("pending-resignup@gachi.com", "기존 회원"));
    SocialAccount oldAccount =
        socialAccountRepository.saveAndFlush(
            SocialAccount.builder()
                .user(oldUser)
                .provider(SocialProvider.KAKAO)
                .providerUserId("kakao-pending-resignup")
                .build());
    oldAccount.requestDisconnect();
    KakaoUnlinkOutbox event =
        kakaoUnlinkOutboxRepository.saveAndFlush(
            KakaoUnlinkOutbox.builder()
                .userId(oldUser.getId())
                .providerUserId("kakao-pending-resignup")
                .build());
    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));

    KakaoLoginStore store = mock(KakaoLoginStore.class);
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity(
            "kakao-pending-resignup", "pending-resignup@gachi.com", "새 회원", null);
    when(store.consume("ticket", "ticket-token")).thenReturn(identity);
    when(store.consume("signup", "signup-token")).thenReturn(identity);
    KakaoClient client = mock(KakaoClient.class);
    KakaoAuthServiceImpl service =
        new KakaoAuthServiceImpl(
            kakaoEnabledAuthProperties(),
            client,
            store,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            kakaoUnlinkOutboxRepository,
            new SocialAccountDisconnectService(
                socialAccountRepository, authRefreshTokenRepository));

    assertThat(
            service.complete(new KakaoCompleteRequest("ticket-token", false), null, null).status())
        .isEqualTo(KakaoCompleteResponse.Status.SIGNUP_REQUIRED);
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isFalse();
    assertThat(socialAccountRepository.findById(oldAccount.getId())).isPresent();
    service.signup(
        new KakaoSignupRequest("signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
        null,
        null);
    User newUser = userRepository.findByEmail(identity.email()).orElseThrow();
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isTrue();
    assertThat(newUser.getId()).isNotEqualTo(oldUser.getId());
    assertThat(socialAccountRepository.findById(oldAccount.getId())).isEmpty();

    new KakaoUnlinkOutboxProcessor(
            kakaoUnlinkOutboxRepository,
            socialAccountRepository,
            userRepository,
            new SocialAccountDisconnectService(socialAccountRepository, authRefreshTokenRepository),
            client)
        .process(event.getId());
    verify(client, never()).unlink(anyString());
    assertThat(
            socialAccountRepository.findByUserIdAndProvider(newUser.getId(), SocialProvider.KAKAO))
        .isPresent();
  }

  @Test
  void linkingToExistingLocalAccountCancelsWithdrawnUsersPendingKakaoUnlink() {
    String email = "pending-link@gachi.com";
    User oldUser = userRepository.saveAndFlush(socialUser(email, "탈퇴 회원"));
    SocialAccount oldAccount =
        socialAccountRepository.saveAndFlush(
            SocialAccount.builder()
                .user(oldUser)
                .provider(SocialProvider.KAKAO)
                .providerUserId("kakao-pending-link")
                .build());
    oldAccount.requestDisconnect();
    KakaoUnlinkOutbox event =
        kakaoUnlinkOutboxRepository.saveAndFlush(
            KakaoUnlinkOutbox.builder()
                .userId(oldUser.getId())
                .providerUserId("kakao-pending-link")
                .build());
    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));
    userRepository.flush();
    OffsetDateTime now = OffsetDateTime.now();
    User localUser =
        userRepository.saveAndFlush(
            User.builder()
                .email(email)
                .loginId("pending_link_user")
                .passwordHash("encoded-password")
                .name("일반 회원")
                .status(UserStatus.ACTIVE)
                .consentAgreedAt(now)
                .consentVersion("2026-04-v1")
                .passwordUpdatedAt(now)
                .build());

    KakaoLoginStore store = mock(KakaoLoginStore.class);
    KakaoClient.KakaoIdentity identity =
        new KakaoClient.KakaoIdentity("kakao-pending-link", email, "일반 회원", null);
    when(store.consume("ticket", "ticket-token")).thenReturn(identity);
    when(store.consume("link", "link-token")).thenReturn(identity);
    KakaoAuthServiceImpl service =
        new KakaoAuthServiceImpl(
            kakaoEnabledAuthProperties(),
            mock(KakaoClient.class),
            store,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            kakaoUnlinkOutboxRepository,
            new SocialAccountDisconnectService(
                socialAccountRepository, authRefreshTokenRepository));

    assertThat(
            service.complete(new KakaoCompleteRequest("ticket-token", false), null, null).status())
        .isEqualTo(KakaoCompleteResponse.Status.LINK_REQUIRED);
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isFalse();
    AuthTokenResponse tokens =
        service.link(localUser.getId(), new KakaoLinkRequest("link-token", false), null, null);

    assertThat(jwtTokenProvider.parseAccessToken(tokens.accessToken()).getUserId())
        .isEqualTo(localUser.getId());
    assertThat(kakaoUnlinkOutboxRepository.findById(event.getId()).orElseThrow().isProcessed())
        .isTrue();
    assertThat(socialAccountRepository.findById(oldAccount.getId())).isEmpty();
    assertThat(
            socialAccountRepository.findByUserIdAndProvider(
                localUser.getId(), SocialProvider.KAKAO))
        .isPresent();
  }

  private User socialUser(String email, String name) {
    OffsetDateTime now = OffsetDateTime.now();
    return User.builder()
        .email(email)
        .name(name)
        .status(UserStatus.ACTIVE)
        .languageCode("KO")
        .notificationPreference(NotificationPreference.IMPORTANT)
        .emailVerifiedAt(now)
        .consentAgreedAt(now)
        .consentVersion("2026-04-v1")
        .passwordUpdatedAt(now)
        .build();
  }

  private AuthProperties kakaoEnabledAuthProperties() {
    AuthProperties.Jwt jwt =
        new AuthProperties.Jwt(
            "gachi-test", "test-secret-key-that-is-longer-than-32-bytes", 15, 7, 30);
    AuthProperties.Email email =
        new AuthProperties.Email("memory", 300, 60, 5, 1800, "", "", "test subject", true);
    AuthProperties.Policy policy = new AuthProperties.Policy(5, 60);
    AuthProperties.RateLimit rateLimit =
        new AuthProperties.RateLimit(
            false, "auth:rate-limit:", "test-hmac-secret", List.of(), policy, policy);
    AuthProperties.Kakao kakao =
        new AuthProperties.Kakao(
            true,
            "test-rest-api-key",
            "test-client-secret",
            "test-admin-key",
            "1546693",
            "http://localhost/api/v1/auth/kakao/callback",
            "gachi://kakao-auth",
            300,
            120,
            600);
    return new AuthProperties("2026-04-v1", jwt, email, rateLimit, kakao);
  }
}
