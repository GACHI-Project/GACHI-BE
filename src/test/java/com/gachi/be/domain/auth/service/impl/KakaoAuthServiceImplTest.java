package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.request.KakaoCompleteRequest;
import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.KakaoCompleteResponse;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.domain.auth.service.KakaoLoginStore;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoAuthServiceImplTest {
  @Mock private AuthProperties authProperties;
  @Mock private AuthProperties.Kakao kakaoProperties;
  @Mock private KakaoClient kakaoClient;
  @Mock private KakaoLoginStore kakaoLoginStore;
  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuthTokenIssuer authTokenIssuer;
  @Mock private AuthRefreshTokenRepository authRefreshTokenRepository;

  private KakaoAuthServiceImpl service;

  @BeforeEach
  void setUp() {
    when(authProperties.getKakao()).thenReturn(kakaoProperties);
    when(kakaoProperties.enabled()).thenReturn(true);
    service =
        new KakaoAuthServiceImpl(
            authProperties,
            kakaoClient,
            kakaoLoginStore,
            socialAccountRepository,
            userRepository,
            authTokenIssuer,
            authRefreshTokenRepository);
  }

  @Test
  void completeReturnsGachiTokensForLinkedUser() {
    KakaoClient.KakaoIdentity identity = identity("kakao-1", "user@gachi.com", "민주", null);
    User user = activeUser("user@gachi.com", "민주", null);
    SocialAccount socialAccount =
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-1")
            .build();
    AuthTokenResponse tokens = tokens();
    when(kakaoLoginStore.consume("ticket", "ticket-1")).thenReturn(identity);
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-1"))
        .thenReturn(Optional.of(socialAccount));
    when(authTokenIssuer.issue(user, true, "device", "127.0.0.1")).thenReturn(tokens);

    KakaoCompleteResponse response =
        service.complete(new KakaoCompleteRequest("ticket-1", true), "device", "127.0.0.1");

    assertThat(response.status()).isEqualTo(KakaoCompleteResponse.Status.LOGIN_SUCCESS);
    assertThat(response.tokens()).isSameAs(tokens);
  }

  @Test
  void completeDoesNotAutomaticallyMergeSameEmailAccount() {
    KakaoClient.KakaoIdentity identity = identity("kakao-2", "USER@gachi.com", "민주", null);
    when(kakaoLoginStore.consume("ticket", "ticket-2")).thenReturn(identity);
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-2"))
        .thenReturn(Optional.empty());
    when(userRepository.existsByEmail("user@gachi.com")).thenReturn(true);
    when(kakaoProperties.signupTokenTtlSeconds()).thenReturn(600L);
    when(kakaoLoginStore.issue(eq("link"), eq(identity), any())).thenReturn("link-token");

    KakaoCompleteResponse response =
        service.complete(new KakaoCompleteRequest("ticket-2", false), null, null);

    assertThat(response.status()).isEqualTo(KakaoCompleteResponse.Status.LINK_REQUIRED);
    assertThat(response.linkToken()).isEqualTo("link-token");
    verify(authTokenIssuer, never()).issue(any(), any(Boolean.class), any(), any());
  }

  @Test
  void signupCreatesSocialOnlyUserWithoutLocalCredentials() {
    KakaoClient.KakaoIdentity identity =
        identity("kakao-3", "new@gachi.com", "새 사용자", "+82 10-1234-5678");
    AuthTokenResponse tokens = tokens();
    when(kakaoLoginStore.consume("signup", "signup-token")).thenReturn(identity);
    when(authProperties.getConsentVersion()).thenReturn("2026-04-v1");
    when(authTokenIssuer.issue(any(User.class), eq(false), eq(null), eq(null))).thenReturn(tokens);

    AuthTokenResponse response =
        service.signup(
            new KakaoSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            null,
            null);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).saveAndFlush(userCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.getLoginId()).isNull();
    assertThat(saved.getPasswordHash()).isNull();
    assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
    assertThat(response).isSameAs(tokens);
    verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
  }

  @Test
  void invalidUnlinkWebhookNeverDeletesLink() {
    when(kakaoProperties.adminKey()).thenReturn("admin-key");

    service.handleUnlinkWebhook("KakaoAK wrong", "1546693", "kakao-4");

    verify(socialAccountRepository, never()).findByProviderAndProviderUserId(any(), any());
  }

  @Test
  void validUnlinkWebhookWithdrawsSocialOnlyUser() {
    User user = activeUser("social@gachi.com", "소셜 회원", null);
    SocialAccount account =
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-5")
            .build();
    when(kakaoProperties.adminKey()).thenReturn("admin-key");
    when(kakaoProperties.appId()).thenReturn("1546693");
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-5"))
        .thenReturn(Optional.of(account));
    when(authRefreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(null)).thenReturn(List.of());

    service.handleUnlinkWebhook("KakaoAK admin-key", "1546693", "kakao-5");

    verify(socialAccountRepository).delete(account);
    assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
    assertThat(user.getDeletedAt()).isNotNull();
  }

  private KakaoClient.KakaoIdentity identity(
      String id, String email, String nickname, String phoneNumber) {
    return new KakaoClient.KakaoIdentity(id, email, nickname, phoneNumber);
  }

  private User activeUser(String email, String name, String phoneNumber) {
    OffsetDateTime now = OffsetDateTime.now();
    return User.builder()
        .email(email)
        .name(name)
        .phoneNumber(phoneNumber)
        .status(UserStatus.ACTIVE)
        .languageCode("KO")
        .notificationPreference(NotificationPreference.IMPORTANT)
        .emailVerifiedAt(now)
        .consentAgreedAt(now)
        .consentVersion("2026-04-v1")
        .passwordUpdatedAt(now)
        .build();
  }

  private AuthTokenResponse tokens() {
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);
    return new AuthTokenResponse(
        "Bearer", "access-token", "refresh-token", expiresAt, expiresAt.plusDays(7), false);
  }
}
