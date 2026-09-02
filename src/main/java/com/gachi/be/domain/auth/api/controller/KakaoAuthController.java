package com.gachi.be.domain.auth.api.controller;

import com.gachi.be.domain.auth.dto.request.KakaoCompleteRequest;
import com.gachi.be.domain.auth.dto.request.KakaoLinkRequest;
import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.KakaoCompleteResponse;
import com.gachi.be.domain.auth.service.AuthRateLimitService;
import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.auth.service.ClientIpExtractor;
import com.gachi.be.domain.auth.service.KakaoAuthService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/kakao")
public class KakaoAuthController {
  private final KakaoAuthService kakaoAuthService;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final AuthRateLimitService authRateLimitService;
  private final ClientIpExtractor clientIpExtractor;

  @GetMapping("/authorize")
  public ResponseEntity<Void> authorize() {
    return redirect(kakaoAuthService.createAuthorizationUri());
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
    return redirect(kakaoAuthService.handleCallback(code, state));
  }

  @PostMapping("/complete")
  public ApiResponse<KakaoCompleteResponse> complete(
      @Valid @RequestBody KakaoCompleteRequest request, HttpServletRequest servletRequest) {
    String clientIp = clientIpExtractor.extractClientIp(servletRequest);
    authRateLimitService.checkLoginRateLimit(clientIp);
    return ApiResponse.success(
        SuccessCode.AUTH_KAKAO_COMPLETE_SUCCESS,
        kakaoAuthService.complete(request, deviceInfo(servletRequest), clientIp));
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthTokenResponse> signup(
      @Valid @RequestBody KakaoSignupRequest request, HttpServletRequest servletRequest) {
    return ApiResponse.success(
        SuccessCode.AUTH_KAKAO_SIGNUP_SUCCESS,
        kakaoAuthService.signup(
            request,
            deviceInfo(servletRequest),
            clientIpExtractor.extractClientIp(servletRequest)));
  }

  @PostMapping("/link")
  public ApiResponse<AuthTokenResponse> link(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @Valid @RequestBody KakaoLinkRequest request,
      HttpServletRequest servletRequest) {
    User user = authenticatedUserResolver.resolveActiveUser(authorization);
    return ApiResponse.success(
        SuccessCode.AUTH_KAKAO_LINK_SUCCESS,
        kakaoAuthService.link(
            user.getId(),
            request,
            deviceInfo(servletRequest),
            clientIpExtractor.extractClientIp(servletRequest)));
  }

  @DeleteMapping("/link")
  public ApiResponse<Void> unlink(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
    User user = authenticatedUserResolver.resolveActiveUser(authorization);
    kakaoAuthService.unlink(user.getId());
    return ApiResponse.success(SuccessCode.AUTH_KAKAO_UNLINK_SUCCESS, null);
  }

  @PostMapping("/unlink-webhook")
  public ResponseEntity<Void> unlinkWebhook(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestParam(name = "app_id", required = false) String appId,
      @RequestParam(name = "user_id", required = false) String providerUserId) {
    kakaoAuthService.handleUnlinkWebhook(authorization, appId, providerUserId);
    return ResponseEntity.ok().build();
  }

  private ResponseEntity<Void> redirect(URI location) {
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }

  private String deviceInfo(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }
}
