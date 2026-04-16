package com.gachi.be.global.security;

import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization Bearer 토큰이 있으면 SecurityContext에 인증 정보를 세팅한다.
 *
 * <p>컨트롤러/서비스 단의 토큰 검증 이전에 "전역 permitAll 우회"를 막기 위해 필터 레벨에서 1차 인증 상태를 만든다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String BEARER_PREFIX = "Bearer ";
  public static final String AUTH_ERROR_CODE_ATTRIBUTE = "authErrorCode";

  private final JwtTokenProvider jwtTokenProvider;
  private final UserRepository userRepository;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = extractBearerToken(request.getHeader("Authorization"));
    if (StringUtils.hasText(token)) {
      try {
        JwtTokenProvider.AccessTokenClaims claims = jwtTokenProvider.parseAccessToken(token);
        User user =
            userRepository
                .findById(claims.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID));
        if (user.getStatus() != UserStatus.ACTIVE) {
          throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
        }

        // 토큰 유효성 + 사용자 활성 상태가 모두 확인된 경우에만 인증 컨텍스트를 설정한다.
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                claims.getUserId(), null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (BusinessException e) {
        // 잘못된 토큰은 인증 컨텍스트를 비워서 보호 경로 접근을 차단한다.
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_CODE_ATTRIBUTE, e.getErrorCode().name());
      }
    } else {
      request.removeAttribute(AUTH_ERROR_CODE_ATTRIBUTE);
    }
    filterChain.doFilter(request, response);
  }

  private String extractBearerToken(String authorizationHeader) {
    if (!StringUtils.hasText(authorizationHeader)) {
      return "";
    }
    String normalizedHeader = authorizationHeader.trim();
    if (normalizedHeader.length() < BEARER_PREFIX.length()
        || !normalizedHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return "";
    }
    return normalizedHeader.substring(BEARER_PREFIX.length()).trim();
  }
}
