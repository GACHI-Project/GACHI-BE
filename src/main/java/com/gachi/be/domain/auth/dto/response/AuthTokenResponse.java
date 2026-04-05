package com.gachi.be.domain.auth.dto.response;

import java.time.OffsetDateTime;

public record AuthTokenResponse(
    String tokenType,
    String accessToken,
    String refreshToken,
    OffsetDateTime accessTokenExpiresAt,
    OffsetDateTime refreshTokenExpiresAt,
    boolean rememberMe) {}
