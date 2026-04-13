package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.config.AuthProperties;
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
import java.util.List;
import java.util.regex.Pattern;
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
  private static final Pattern IPV4_LITERAL_PATTERN =
      Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
  private static final Pattern IPV6_LITERAL_PATTERN = Pattern.compile("^(?=.*:)[0-9a-fA-F:]+$");

  private final AuthService authService;
  private final AuthRateLimitService authRateLimitService;
  private final AuthProperties authProperties;

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

    // nginx가 X-Forwarded-For를 append하는 구성일 수 있으므로 위조 영향이 적은 X-Real-IP를 우선 사용한다.
    String realIp = normalizeIp(request.getHeader("X-Real-IP"));
    if (StringUtils.hasText(realIp)) {
      return realIp;
    }

    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(forwardedFor)) {
      String lastHop = extractLastForwardedIp(forwardedFor);
      if (StringUtils.hasText(lastHop)) {
        return lastHop;
      }
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
    List<String> trustedProxies = authProperties.getRateLimit().getTrustedProxies();
    if (trustedProxies == null || trustedProxies.isEmpty()) {
      return false;
    }
    return trustedProxies.stream().anyMatch(proxy -> matchesTrustedProxy(remoteAddr, proxy));
  }

  private boolean matchesTrustedProxy(String remoteAddr, String trustedProxy) {
    String normalizedTrustedProxy = normalizeIp(trustedProxy);
    if (!StringUtils.hasText(normalizedTrustedProxy)) {
      return false;
    }
    if (normalizedTrustedProxy.contains("/")) {
      return isInCidr(remoteAddr, normalizedTrustedProxy);
    }
    return normalizedTrustedProxy.equals(remoteAddr);
  }

  private boolean isInCidr(String remoteAddr, String cidr) {
    String[] split = cidr.split("/");
    if (split.length != 2) {
      return false;
    }
    if (!isIpLiteral(remoteAddr) || !isIpLiteral(split[0])) {
      return false;
    }
    try {
      InetAddress remoteAddress = InetAddress.getByName(remoteAddr);
      InetAddress networkAddress = InetAddress.getByName(split[0]);
      int prefixLength = Integer.parseInt(split[1]);
      byte[] remoteBytes = remoteAddress.getAddress();
      byte[] networkBytes = networkAddress.getAddress();
      if (remoteBytes.length != networkBytes.length || prefixLength < 0) {
        return false;
      }
      if (prefixLength > remoteBytes.length * 8) {
        return false;
      }

      int fullBytes = prefixLength / 8;
      int remainingBits = prefixLength % 8;
      for (int i = 0; i < fullBytes; i++) {
        if (remoteBytes[i] != networkBytes[i]) {
          return false;
        }
      }
      if (remainingBits == 0) {
        return true;
      }

      int mask = (-1) << (8 - remainingBits);
      return (remoteBytes[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
    } catch (UnknownHostException | NumberFormatException e) {
      return false;
    }
  }

  private boolean isIpLiteral(String value) {
    String normalized = normalizeIp(value);
    return IPV4_LITERAL_PATTERN.matcher(normalized).matches()
        || IPV6_LITERAL_PATTERN.matcher(normalized).matches();
  }

  private String extractLastForwardedIp(String forwardedFor) {
    String[] split = forwardedFor.split(",");
    for (int i = split.length - 1; i >= 0; i--) {
      String candidate = normalizeIp(split[i]);
      if (StringUtils.hasText(candidate)) {
        return candidate;
      }
    }
    return "";
  }
}
