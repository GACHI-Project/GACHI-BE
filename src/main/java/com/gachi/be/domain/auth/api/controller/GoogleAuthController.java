package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.dto.request.GoogleCompleteRequest;
import com.gachi.be.domain.auth.dto.request.GoogleLinkRequest;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.GoogleCompleteResponse;
import com.gachi.be.domain.auth.service.AuthRateLimitService;
import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.auth.service.ClientIpExtractor;
import com.gachi.be.domain.auth.service.GoogleAuthService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
  private final GoogleAuthService googleAuthService;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final AuthRateLimitService authRateLimitService;
  private final ClientIpExtractor clientIpExtractor;

  @PostMapping("/complete")
  public ApiResponse<GoogleCompleteResponse> complete(
      @Valid @RequestBody GoogleCompleteRequest request, HttpServletRequest servletRequest) {
    String clientIp = clientIpExtractor.extractClientIp(servletRequest);
    authRateLimitService.checkLoginRateLimit(clientIp);
    return ApiResponse.success(
        SuccessCode.AUTH_GOOGLE_COMPLETE_SUCCESS,
        googleAuthService.complete(request, deviceInfo(servletRequest), clientIp));
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthTokenResponse> signup(
      @Valid @RequestBody GoogleSignupRequest request, HttpServletRequest servletRequest) {
    return ApiResponse.success(
        SuccessCode.AUTH_GOOGLE_SIGNUP_SUCCESS,
        googleAuthService.signup(
            request,
            deviceInfo(servletRequest),
            clientIpExtractor.extractClientIp(servletRequest)));
  }

  @PostMapping("/link")
  public ApiResponse<AuthTokenResponse> link(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody GoogleLinkRequest request,
      HttpServletRequest servletRequest) {
    User user = authenticatedUserResolver.resolveActiveUser(authorization);
    return ApiResponse.success(
        SuccessCode.AUTH_GOOGLE_LINK_SUCCESS,
        googleAuthService.link(
            user.getId(),
            request,
            deviceInfo(servletRequest),
            clientIpExtractor.extractClientIp(servletRequest)));
  }

  private String deviceInfo(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }
}
