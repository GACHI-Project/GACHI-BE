package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.dto.request.CheckEmailRequest;
import com.gachi.be.domain.auth.dto.request.CheckLoginIdRequest;
import com.gachi.be.domain.auth.dto.request.CheckPhoneNumberRequest;
import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.DuplicateCheckResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;
import com.gachi.be.domain.auth.service.AuthRateLimitService;
import com.gachi.be.domain.auth.service.AuthService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API 엔드포인트를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;
  private final AuthRateLimitService authRateLimitService;

  @PostMapping("/check-login-id")
  public ApiResponse<DuplicateCheckResponse> checkLoginId(
      @Valid @RequestBody CheckLoginIdRequest request) {
    return ApiResponse.success(
        SuccessCode.AUTH_CHECK_LOGIN_ID_AVAILABLE, authService.checkLoginId(request));
  }

  @PostMapping("/check-email")
  public ApiResponse<DuplicateCheckResponse> checkEmail(
      @Valid @RequestBody CheckEmailRequest request) {
    return ApiResponse.success(
        SuccessCode.AUTH_CHECK_EMAIL_AVAILABLE, authService.checkEmail(request));
  }

  @PostMapping("/check-phone-number")
  public ApiResponse<DuplicateCheckResponse> checkPhoneNumber(
      @Valid @RequestBody CheckPhoneNumberRequest request) {
    return ApiResponse.success(
        SuccessCode.AUTH_CHECK_PHONE_NUMBER_AVAILABLE, authService.checkPhoneNumber(request));
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ApiResponse.success(SuccessCode.AUTH_SIGNUP_SUCCESS, authService.signup(request));
  }

  @PostMapping("/login")
  public ApiResponse<AuthTokenResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String clientIp = extractClientIp(servletRequest);
    authRateLimitService.checkLoginRateLimit(clientIp);
    return ApiResponse.success(
        SuccessCode.AUTH_LOGIN_SUCCESS,
        authService.login(request, extractDeviceInfo(servletRequest), clientIp));
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
      @Valid @RequestBody EmailSendRequest request, HttpServletRequest servletRequest) {
    authRateLimitService.checkEmailSendRateLimit(extractClientIp(servletRequest), request.email());
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
    String remoteAddr = normalizeIp(request.getRemoteAddr());
    if (!isTrustedProxy(remoteAddr)) {
      return remoteAddr;
    }

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String[] split = forwardedFor.split(",");
      String firstHop = normalizeIp(split[0]);
      if (StringUtils.hasText(firstHop)) {
        return firstHop;
      }
    }
    String realIp = request.getHeader("X-Real-IP");
    if (StringUtils.hasText(realIp)) {
      return normalizeIp(realIp);
    }
    return remoteAddr;
  }

  private String normalizeIp(String rawIp) {
    return StringUtils.hasText(rawIp) ? rawIp.trim() : "";
  }

  private boolean isTrustedProxy(String remoteAddr) {
    if (!StringUtils.hasText(remoteAddr)) {
      return false;
    }
    try {
      InetAddress address = InetAddress.getByName(remoteAddr);
      // 신뢰 가능한 프록시(내부망/루프백)에서 온 요청만 Forwarded 헤더를 신뢰한다.
      return address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isSiteLocalAddress()
          || address.isLinkLocalAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }
}
