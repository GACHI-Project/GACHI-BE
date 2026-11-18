package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.KakaoAuthService;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.domain.auth.service.KakaoLoginStore;
import com.gachi.be.domain.user.dto.request.UserWithdrawalRequest;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.domain.user.service.UserProfileService;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "app.auth.kakao.enabled=true",
      "app.auth.kakao.rest-api-key=test-rest-key",
      "app.auth.kakao.client-secret=test-client-secret",
      "app.auth.kakao.admin-key=test-admin-key",
      "app.auth.kakao.app-id=test-app-id",
      "app.auth.kakao.redirect-uri=http://localhost/kakao/callback"
    })
class KakaoResignupRollbackIntegrationTest {
  @Autowired private KakaoAuthService kakaoAuthService;
  @Autowired private UserProfileService userProfileService;
  @Autowired private UserRepository userRepository;
  @Autowired private SocialAccountRepository socialAccountRepository;
  @Autowired private KakaoUnlinkOutboxRepository outboxRepository;
  @MockitoBean private KakaoLoginStore kakaoLoginStore;
  @MockitoBean private AuthTokenIssuer authTokenIssuer;

  @Test
  void failedSignupKeepsPendingUnlinkAndOldConnection() {
    String email = "rollback-resignup@gachi.com";
    String providerUserId = "kakao-rollback-resignup";
    OffsetDateTime now = OffsetDateTime.now();
    User oldUser =
        userRepository.saveAndFlush(
            User.builder()
                .email(email)
                .name("탈퇴 회원")
                .status(UserStatus.ACTIVE)
                .consentAgreedAt(now)
                .consentVersion("2026-04-v1")
                .passwordUpdatedAt(now)
                .build());
    SocialAccount oldAccount =
        socialAccountRepository.saveAndFlush(
            SocialAccount.builder()
                .user(oldUser)
                .provider(SocialProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    oldAccount.requestDisconnect();
    socialAccountRepository.saveAndFlush(oldAccount);
    KakaoUnlinkOutbox event =
        outboxRepository.saveAndFlush(
            KakaoUnlinkOutbox.builder()
                .userId(oldUser.getId())
                .providerUserId(providerUserId)
                .build());
    userProfileService.withdraw(oldUser, new UserWithdrawalRequest(null));

    when(kakaoLoginStore.consume("signup", "signup-token"))
        .thenReturn(new KakaoClient.KakaoIdentity(providerUserId, email, "재가입", null));
    when(authTokenIssuer.issue(any(User.class), eq(false), isNull(), isNull()))
        .thenThrow(new IllegalStateException("token issuance failed after save"));

    assertThatThrownBy(
            () ->
                kakaoAuthService.signup(
                    new KakaoSignupRequest("signup-token", true, "KO", null, false), null, null))
        .isInstanceOf(IllegalStateException.class);

    assertThat(userRepository.findByEmail(email)).isEmpty();
    assertThat(socialAccountRepository.findById(oldAccount.getId())).isPresent();
    assertThat(outboxRepository.findById(event.getId()).orElseThrow().isProcessed()).isFalse();
  }
}
