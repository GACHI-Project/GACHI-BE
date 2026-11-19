package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
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

  public void requireUnlinkAllowed(User user, SocialProvider provider) {
    if (!hasPasswordLogin(user)
        && socialAccountRepository.findAllByUserIdForUpdate(user.getId()).stream()
            .noneMatch(
                account -> account.getProvider() != provider && !account.isDisconnectPending())) {
      throw new BusinessException(ErrorCode.AUTH_LAST_LOGIN_METHOD);
    }
  }

  public void disconnect(SocialAccount account) {
    var user = account.getUser();
    socialAccountRepository.delete(account);
    if (!hasPasswordLogin(user)
        && socialAccountRepository.findAllByUserIdForUpdate(user.getId()).stream()
            .noneMatch(
                other ->
                    other.getProvider() != account.getProvider() && !other.isDisconnectPending())) {
      user.withdraw(OffsetDateTime.now());
      socialAccountRepository.deleteWithdrawnUserLinks(user.getId());
      authRefreshTokenRepository
          .findAllByUserIdAndRevokedAtIsNull(user.getId())
          .forEach(token -> token.revoke());
    }
  }

  private boolean hasPasswordLogin(User user) {
    return StringUtils.hasText(user.getLoginId()) && StringUtils.hasText(user.getPasswordHash());
  }
}
