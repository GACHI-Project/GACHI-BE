package com.gachi.be.global.security;

import com.gachi.be.global.code.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 인증 실패 시 기존 API 에러 포맷(ApiResponse)과 에러코드 계약을 유지한다.
 *
 * <p>기존 통합테스트/클라이언트가 AUTH4015, AUTH4016 코드를 기반으로 분기하고 있어 보안 필터 도입 시에도 동일 응답을 보장한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    ErrorCode errorCode = resolveErrorCode(request);

    response.setStatus(errorCode.getHttpStatus().value());
    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write(
            """
            {
              "isSuccess": false,
              "status": "%s",
              "code": "%s",
              "message": "%s"
            }
            """
                .formatted(
                    errorCode.getHttpStatus().name(),
                    escapeJson(errorCode.getCode()),
                    escapeJson(errorCode.getMessage())));
  }

  private ErrorCode resolveErrorCode(HttpServletRequest request) {
    Object errorCodeAttribute =
        request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_CODE_ATTRIBUTE);
    if (errorCodeAttribute instanceof String errorCodeName && StringUtils.hasText(errorCodeName)) {
      try {
        return ErrorCode.valueOf(errorCodeName);
      } catch (IllegalArgumentException ignored) {
        // 정의되지 않은 값은 아래 기본 분기로 처리한다.
      }
    }

    String authorizationHeader = request.getHeader("Authorization");
    if (!StringUtils.hasText(authorizationHeader)) {
      return ErrorCode.AUTH_ACCESS_TOKEN_MISSING;
    }
    return ErrorCode.AUTH_ACCESS_TOKEN_INVALID;
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
