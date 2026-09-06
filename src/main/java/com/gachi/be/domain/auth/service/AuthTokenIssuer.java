package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 인증 방식과 무관하게 GACHI access/refresh token을 발급하고 refresh 세션을 저장한다. */
@Component
@RequiredArgsConstructor
public class AuthTokenIssuer {
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final TokenHashService tokenHashService;
  private final AuthProperties authProperties;

  public AuthTokenResponse issue(
      User user, boolean rememberMe, String deviceInfo, String ipAddress) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime refreshExpiresAt =
        now.plusDays(
            rememberMe
                ? authProperties.getJwt().getRefreshTokenRememberDays()
                : authProperties.getJwt().getRefreshTokenDays());
    JwtTokenProvider.JwtToken accessToken = jwtTokenProvider.issueAccessToken(user);
    String jti = UUID.randomUUID().toString();
    JwtTokenProvider.JwtToken refreshToken =
        jwtTokenProvider.issueRefreshToken(user, jti, refreshExpiresAt);

    authRefreshTokenRepository.save(
        AuthRefreshToken.builder()
            .user(user)
            .tokenHash(tokenHashService.sha256(refreshToken.getToken()))
            .jti(jti)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .rememberMe(rememberMe)
            .expiresAt(refreshToken.getExpiresAt())
            .lastUsedAt(now)
            .build());

    return new AuthTokenResponse(
        "Bearer",
        accessToken.getToken(),
        refreshToken.getToken(),
        accessToken.getExpiresAt(),
        refreshToken.getExpiresAt(),
        rememberMe);
  }
}
