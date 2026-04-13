package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authorization Bearer 헤더에서 로그인 사용자를 해석한다.
 *
 * <p>현재 프로젝트는 전역 Security Filter를 강제하지 않기 때문에, 인증이 필요한 도메인에서 재사용할 수 있도록 별도 컴포넌트로 분리한다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  public User resolveActiveUser(String authorizationHeader) {
    String token = extractAccessToken(authorizationHeader);
    JwtTokenProvider.AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
    User user =
        userRepository
            .findById(claims.getUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }
    return user;
  }

  private String extractAccessToken(String authorizationHeader) {
    if (!StringUtils.hasText(authorizationHeader)) {
      throw new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_MISSING);
    }
    if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
      throw new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
    }

    String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    if (!StringUtils.hasText(token)) {
      throw new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID);
    }
    return token;
  }
}
