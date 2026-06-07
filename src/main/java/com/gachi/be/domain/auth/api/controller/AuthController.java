package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.dto.request.CheckEmailRequest;
import com.gachi.be.domain.auth.dto.request.CheckLoginIdRequest;
import com.gachi.be.domain.auth.dto.request.CheckPhoneNumberRequest;
import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.FindLoginIdEmailSendRequest;
import com.gachi.be.domain.auth.dto.request.FindLoginIdEmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetEmailSendRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetEmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.DuplicateCheckResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.FindLoginIdResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;
import com.gachi.be.domain.auth.service.AuthRateLimitService;
import com.gachi.be.domain.auth.service.AuthService;
import com.gachi.be.domain.auth.service.ClientIpExtractor;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
  private final ClientIpExtractor clientIpExtractor;

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
    String clientIp = clientIpExtractor.extractClientIp(servletRequest);
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
            request,
            extractDeviceInfo(servletRequest),
            clientIpExtractor.extractClientIp(servletRequest)));
  }

  @PostMapping("/email/send")
  public ApiResponse<EmailSendResponse> sendEmailVerificationCode(
      @Valid @RequestBody EmailSendRequest request, HttpServletRequest servletRequest) {
    authRateLimitService.checkEmailSendRateLimit(
        clientIpExtractor.extractClientIp(servletRequest), request.email());
    return ApiResponse.success(
        SuccessCode.AUTH_EMAIL_CODE_SENT, authService.sendEmailVerificationCode(request));
  }

  @PostMapping("/email/verify")
  public ApiResponse<Void> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
    authService.verifyEmailCode(request);
    return ApiResponse.success(SuccessCode.AUTH_EMAIL_VERIFIED, null);
  }

  @PostMapping("/find-login-id/email/send")
  public ApiResponse<EmailSendResponse> sendFindLoginIdEmailVerificationCode(
      @Valid @RequestBody FindLoginIdEmailSendRequest request, HttpServletRequest servletRequest) {
    authRateLimitService.checkFindLoginIdEmailSendRateLimit(
        clientIpExtractor.extractClientIp(servletRequest), request.email());
    return ApiResponse.success(
        SuccessCode.AUTH_FIND_LOGIN_ID_EMAIL_CODE_SENT,
        authService.sendFindLoginIdEmailVerificationCode(request));
  }

  @PostMapping("/find-login-id/email/verify")
  public ApiResponse<FindLoginIdResponse> verifyFindLoginIdEmailCode(
      @Valid @RequestBody FindLoginIdEmailVerifyRequest request) {
    return ApiResponse.success(
        SuccessCode.AUTH_FIND_LOGIN_ID_SUCCESS, authService.verifyFindLoginIdEmailCode(request));
  }

  @PostMapping("/password-reset/email/send")
  public ApiResponse<EmailSendResponse> sendPasswordResetEmailVerificationCode(
      @Valid @RequestBody PasswordResetEmailSendRequest request,
      HttpServletRequest servletRequest) {
    authRateLimitService.checkPasswordResetEmailSendRateLimit(
        clientIpExtractor.extractClientIp(servletRequest), request.email());
    return ApiResponse.success(
        SuccessCode.AUTH_PASSWORD_RESET_EMAIL_CODE_SENT,
        authService.sendPasswordResetEmailVerificationCode(request));
  }

  @PostMapping("/password-reset/email/verify")
  public ApiResponse<Void> verifyPasswordResetEmailCode(
      @Valid @RequestBody PasswordResetEmailVerifyRequest request) {
    authService.verifyPasswordResetEmailCode(request);
    return ApiResponse.success(SuccessCode.AUTH_PASSWORD_RESET_EMAIL_VERIFIED, null);
  }

  @PostMapping("/password-reset")
  public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
    authService.resetPassword(request);
    return ApiResponse.success(SuccessCode.AUTH_PASSWORD_RESET_SUCCESS, null);
  }

  private String extractDeviceInfo(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }
}
