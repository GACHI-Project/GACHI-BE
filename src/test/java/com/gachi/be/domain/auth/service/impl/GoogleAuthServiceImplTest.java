package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.dto.request.GoogleCompleteRequest;
import com.gachi.be.domain.auth.dto.request.GoogleLinkRequest;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.GoogleCompleteResponse;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.domain.auth.service.GoogleLoginStore;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceImplTest {
  @Mock private AuthProperties authProperties;
  @Mock private GoogleClient googleClient;
  @Mock private GoogleLoginStore googleLoginStore;
  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuthTokenIssuer authTokenIssuer;

  private GoogleAuthServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new GoogleAuthServiceImpl(
            authProperties,
            new GoogleAuthProperties(true, List.of("client-id"), 1800),
            googleClient,
            googleLoginStore,
            socialAccountRepository,
            userRepository,
            authTokenIssuer);
  }

  @Test
  void completeReturnsGachiTokensForLinkedUser() {
    GoogleClient.GoogleIdentity identity = identity("google-1", "user@gachi.com", true, "민주");
    User user = activeUser("user@gachi.com", "민주");
    SocialAccount account =
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.GOOGLE)
            .providerUserId("google-1")
            .build();
    AuthTokenResponse tokens = tokens();
    when(googleClient.verify("id-token")).thenReturn(identity);
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-1"))
        .thenReturn(Optional.of(account));
    when(authTokenIssuer.issue(user, true, "device", "127.0.0.1")).thenReturn(tokens);

    GoogleCompleteResponse response =
        service.complete(new GoogleCompleteRequest("id-token", true), "device", "127.0.0.1");

    assertThat(response.status()).isEqualTo(GoogleCompleteResponse.Status.LOGIN_SUCCESS);
    assertThat(response.tokens()).isSameAs(tokens);
  }

  @Test
  void completeDoesNotAutomaticallyMergeSameEmailAccount() {
    GoogleClient.GoogleIdentity identity = identity("google-2", "USER@gachi.com", true, "민주");
    when(googleClient.verify("id-token")).thenReturn(identity);
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-2"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("user@gachi.com"))
        .thenReturn(Optional.of(activeUser("user@gachi.com", "기존 회원")));
    when(googleLoginStore.issue(eq("link"), eq(identity), any())).thenReturn("link-token");

    GoogleCompleteResponse response =
        service.complete(new GoogleCompleteRequest("id-token", false), null, null);

    assertThat(response.status()).isEqualTo(GoogleCompleteResponse.Status.LINK_REQUIRED);
    assertThat(response.linkToken()).isEqualTo("link-token");
    verify(authTokenIssuer, never()).issue(any(), any(Boolean.class), any(), any());
  }

  @Test
  void completeRejectsUnverifiedEmail() {
    when(googleClient.verify("id-token"))
        .thenReturn(identity("google-3", "user@gachi.com", false, "민주"));

    assertBusinessError(
        () -> service.complete(new GoogleCompleteRequest("id-token", false), null, null),
        ErrorCode.AUTH_GOOGLE_EMAIL_REQUIRED);
  }

  @Test
  void completeRejectsWithdrawnAccountWithSameEmail() {
    GoogleClient.GoogleIdentity identity = identity("google-7", "user@gachi.com", true, "민주");
    User withdrawnUser = activeUser("user@gachi.com", "탈퇴 회원");
    withdrawnUser.withdraw(OffsetDateTime.now());
    when(googleClient.verify("id-token")).thenReturn(identity);
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-7"))
        .thenReturn(Optional.empty());
    when(userRepository.findByEmail("user@gachi.com")).thenReturn(Optional.of(withdrawnUser));

    assertBusinessError(
        () -> service.complete(new GoogleCompleteRequest("id-token", false), null, null),
        ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    verify(googleLoginStore, never()).issue(any(), any(), any());
  }

  @Test
  void signupCreatesSocialOnlyUserWithoutPhoneOrLocalCredentials() {
    GoogleClient.GoogleIdentity identity = identity("google-4", "new@gachi.com", true, "새 사용자");
    AuthTokenResponse tokens = tokens();
    when(googleLoginStore.consume("signup", "signup-token")).thenReturn(identity);
    when(authProperties.getConsentVersion()).thenReturn("2026-04-v1");
    when(authTokenIssuer.issue(any(User.class), eq(false), eq(null), eq(null))).thenReturn(tokens);

    AuthTokenResponse response =
        service.signup(
            new GoogleSignupRequest(
                "signup-token", true, "KO", NotificationPreference.IMPORTANT, false),
            null,
            null);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
    verify(userRepository).saveAndFlush(userCaptor.capture());
    verify(socialAccountRepository).saveAndFlush(accountCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.getLoginId()).isNull();
    assertThat(saved.getPasswordHash()).isNull();
    assertThat(saved.getPhoneNumber()).isNull();
    assertThat(accountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
    assertThat(accountCaptor.getValue().getProviderUserId()).isEqualTo("google-4");
    assertThat(response).isSameAs(tokens);
  }

  @Test
  void linkRequiresSameEmailAsCurrentUser() {
    GoogleClient.GoogleIdentity identity = identity("google-5", "different@gachi.com", true, "민주");
    User user = activeUser("user@gachi.com", "민주");
    when(googleLoginStore.consume("link", "link-token")).thenReturn(identity);
    when(userRepository.findByIdWithLock(42L)).thenReturn(Optional.of(user));

    assertBusinessError(
        () -> service.link(42L, new GoogleLinkRequest("link-token", false), null, null),
        ErrorCode.AUTH_GOOGLE_LINK_EMAIL_MISMATCH);
    verify(socialAccountRepository, never()).saveAndFlush(any());
  }

  @Test
  void linkConnectsGoogleAccountToCurrentUser() {
    GoogleClient.GoogleIdentity identity = identity("google-6", "USER@gachi.com", true, "민주");
    User user = activeUser("user@gachi.com", "민주");
    ReflectionTestUtils.setField(user, "id", 42L);
    AuthTokenResponse tokens = tokens();
    when(googleLoginStore.consume("link", "link-token")).thenReturn(identity);
    when(userRepository.findByIdWithLock(42L)).thenReturn(Optional.of(user));
    when(socialAccountRepository.findByUserIdAndProvider(42L, SocialProvider.GOOGLE))
        .thenReturn(Optional.empty());
    when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-6"))
        .thenReturn(Optional.empty());
    when(authTokenIssuer.issue(user, false, null, null)).thenReturn(tokens);

    AuthTokenResponse response =
        service.link(42L, new GoogleLinkRequest("link-token", false), null, null);

    ArgumentCaptor<SocialAccount> accountCaptor = ArgumentCaptor.forClass(SocialAccount.class);
    verify(socialAccountRepository).saveAndFlush(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getUser()).isSameAs(user);
    assertThat(accountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
    assertThat(response).isSameAs(tokens);
  }

  private void assertBusinessError(Runnable action, ErrorCode errorCode) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
  }

  private GoogleClient.GoogleIdentity identity(
      String id, String email, boolean emailVerified, String nickname) {
    return new GoogleClient.GoogleIdentity(id, email, emailVerified, nickname);
  }

  private User activeUser(String email, String name) {
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

  private AuthTokenResponse tokens() {
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(15);
    return new AuthTokenResponse(
        "Bearer", "access-token", "refresh-token", expiresAt, expiresAt.plusDays(7), false);
  }
}
