package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** JWT 발급/검증을 담당한다. */
@Component
public class JwtTokenProvider {
  private static final String CLAIM_TYPE = "type";
  private static final String CLAIM_LOGIN_ID = "loginId";

  private final AuthProperties authProperties;

  public JwtTokenProvider(AuthProperties authProperties) {
    this.authProperties = authProperties;
  }

  public JwtToken issueAccessToken(User user) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime expiresAt = now.plusMinutes(authProperties.getJwt().getAccessTokenMinutes());

    String token =
        Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .issuer(authProperties.getJwt().getIssuer())
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(expiresAt.toInstant()))
            .claim(CLAIM_TYPE, "access")
            .claim(CLAIM_LOGIN_ID, user.getLoginId())
            .signWith(signingKey())
            .compact();

    return new JwtToken(token, expiresAt);
  }

  public JwtToken issueRefreshToken(User user, String jti, OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now();

    String token =
        Jwts.builder()
            .id(jti)
            .subject(String.valueOf(user.getId()))
            .issuer(authProperties.getJwt().getIssuer())
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(expiresAt.toInstant()))
            .claim(CLAIM_TYPE, "refresh")
            .claim(CLAIM_LOGIN_ID, user.getLoginId())
            .signWith(signingKey())
            .compact();

    return new JwtToken(token, expiresAt);
  }

  public RefreshTokenClaims parseRefreshToken(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();

      Object type = claims.get(CLAIM_TYPE);
      if (!"refresh".equals(type)) {
        throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
      }
      if (!authProperties.getJwt().getIssuer().equals(claims.getIssuer())) {
        throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
      }

      return new RefreshTokenClaims(Long.parseLong(claims.getSubject()), claims.getId());
    } catch (ExpiredJwtException e) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    } catch (JwtException | IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }
  }

  private SecretKey signingKey() {
    String secret = authProperties.getJwt().getSecret();
    if (!StringUtils.hasText(secret)) {
      throw new IllegalStateException("app.auth.jwt.secret must be configured.");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
  }

  @Getter
  public static class JwtToken {
    private final String token;
    private final OffsetDateTime expiresAt;

    public JwtToken(String token, OffsetDateTime expiresAt) {
      this.token = token;
      this.expiresAt = expiresAt;
    }
  }

  @Getter
  public static class RefreshTokenClaims {
    private final Long userId;
    private final String jti;

    public RefreshTokenClaims(Long userId, String jti) {
      this.userId = userId;
      this.jti = jti;
    }
  }
}
