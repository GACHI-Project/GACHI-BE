package com.gachi.be.domain.auth.dto.response;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthTokenResponse {
  private String tokenType;
  private String accessToken;
  private String refreshToken;
  private OffsetDateTime accessTokenExpiresAt;
  private OffsetDateTime refreshTokenExpiresAt;
  private boolean rememberMe;
}
