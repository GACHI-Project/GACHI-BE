package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SocialAccountDisconnectServiceTest {
  @Test
  void disabledRemainingProviderCannotKeepSocialOnlyAccountAccessible() {
    SocialAccountRepository accounts = mock(SocialAccountRepository.class);
    AuthProperties auth = mock(AuthProperties.class);
    GoogleAuthProperties google = mock(GoogleAuthProperties.class);
    User user =
        User.builder().email("social@gachi.com").name("회원").status(UserStatus.ACTIVE).build();
    ReflectionTestUtils.setField(user, "id", 42L);
    SocialAccount target =
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.KAKAO)
            .providerUserId("kakao-42")
            .build();
    SocialAccount disabledGoogle =
        SocialAccount.builder()
            .user(user)
            .provider(SocialProvider.GOOGLE)
            .providerUserId("google-42")
            .build();
    when(accounts.findAllByUserIdForUpdate(42L)).thenReturn(List.of(target, disabledGoogle));
    SocialAccountDisconnectService service =
        new SocialAccountDisconnectService(
            accounts, mock(AuthRefreshTokenRepository.class), auth, google);

    assertThatThrownBy(() -> service.requireUnlinkAllowed(user, SocialProvider.KAKAO))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.AUTH_LAST_LOGIN_METHOD));
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }
}
