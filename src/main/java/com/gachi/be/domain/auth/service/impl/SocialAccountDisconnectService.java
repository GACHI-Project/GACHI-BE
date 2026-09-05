package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SocialAccountDisconnectService {
  private final SocialAccountRepository socialAccountRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;

  public void disconnect(SocialAccount account) {
    var user = account.getUser();
    socialAccountRepository.delete(account);
    if (!StringUtils.hasText(user.getLoginId()) || !StringUtils.hasText(user.getPasswordHash())) {
      user.withdraw(OffsetDateTime.now());
      authRefreshTokenRepository
          .findAllByUserIdAndRevokedAtIsNull(user.getId())
          .forEach(token -> token.revoke());
    }
  }
}
