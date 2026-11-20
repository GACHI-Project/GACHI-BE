package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.EmailVerificationPurpose;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SocialAccountDisconnectService {
  private final SocialAccountRepository socialAccountRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final AuthProperties authProperties;
  private final GoogleAuthProperties googleAuthProperties;
  private final EmailVerificationStore emailVerificationStore;

  public void requireUnlinkAllowed(User user, SocialProvider provider) {
    if (!hasPasswordLogin(user)
        && socialAccountRepository.findAllByUserIdForUpdate(user.getId()).stream()
            .noneMatch(account -> account.getProvider() != provider && isUsable(account))) {
      throw new BusinessException(ErrorCode.AUTH_LAST_LOGIN_METHOD);
    }
  }

  public void disconnect(SocialAccount account) {
    var user = account.getUser();
    socialAccountRepository.delete(account);
    if (!hasPasswordLogin(user)
        && socialAccountRepository.findAllByUserIdForUpdate(user.getId()).stream()
            .noneMatch(other -> other.getProvider() != account.getProvider() && isUsable(other))) {
      String withdrawnEmail = user.getEmail();
      clearPasswordResetVerification(withdrawnEmail);
      user.withdraw(OffsetDateTime.now());
      socialAccountRepository.deleteWithdrawnUserLinks(user.getId());
      authRefreshTokenRepository
          .findAllByUserIdAndRevokedAtIsNull(user.getId())
          .forEach(token -> token.revoke());
    }
  }

  private void clearPasswordResetVerification(String email) {
    emailVerificationStore.rollbackIssuedCode(email, EmailVerificationPurpose.RESET_PASSWORD);
    emailVerificationStore.consumeVerifiedEmail(email, EmailVerificationPurpose.RESET_PASSWORD);
  }

  private boolean hasPasswordLogin(User user) {
    return StringUtils.hasText(user.getLoginId()) && StringUtils.hasText(user.getPasswordHash());
  }

  private boolean isUsable(SocialAccount account) {
    if (account.isDisconnectPending()) {
      return false;
    }
    return switch (account.getProvider()) {
      case KAKAO -> authProperties.getKakao().enabled();
      case GOOGLE -> googleAuthProperties.enabled();
    };
  }
}
