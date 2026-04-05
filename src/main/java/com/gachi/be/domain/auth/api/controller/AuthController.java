package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;
import com.gachi.be.domain.auth.service.AuthService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API 엔드포인트를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;

  @PostMapping("/signup")
  public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ApiResponse.success(SuccessCode.AUTH_SIGNUP_SUCCESS, authService.signup(request));
  }

  @PostMapping("/login")
  public ApiResponse<AuthTokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return ApiResponse.success(
        SuccessCode.AUTH_LOGIN_SUCCESS,
        authService.login(
            request, extractDeviceInfo(servletRequest), extractClientIp(servletRequest)));
  }

  @PostMapping("/reissue")
  public ApiResponse<AuthTokenResponse> reissue(
      @Valid @RequestBody ReissueRequest request, HttpServletRequest servletRequest) {
    return ApiResponse.success(
        SuccessCode.AUTH_REISSUE_SUCCESS,
        authService.reissue(
            request, extractDeviceInfo(servletRequest), extractClientIp(servletRequest)));
  }

  @PostMapping("/email/send")
  public ApiResponse<EmailSendResponse> sendEmailVerificationCode(
      @Valid @RequestBody EmailSendRequest request) {
    return ApiResponse.success(
        SuccessCode.AUTH_EMAIL_CODE_SENT, authService.sendEmailVerificationCode(request));
  }

  @PostMapping("/email/verify")
  public ApiResponse<Void> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
    authService.verifyEmailCode(request);
    return ApiResponse.success(SuccessCode.AUTH_EMAIL_VERIFIED, null);
  }

  private String extractDeviceInfo(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  private String extractClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String[] split = forwardedFor.split(",");
      return split[0].trim();
    }
    return request.getRemoteAddr();
  }
}
