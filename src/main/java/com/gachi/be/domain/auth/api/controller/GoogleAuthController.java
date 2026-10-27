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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Google Auth", description = "Google 간편 로그인, 회원가입 및 계정 연동 API")
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
  private final GoogleAuthService googleAuthService;
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final AuthRateLimitService authRateLimitService;
  private final ClientIpExtractor clientIpExtractor;

  @Operation(
      summary = "Google 인증 완료",
      description =
          "Google ID Token을 검증합니다. 응답 status는 LOGIN_SUCCESS, SIGNUP_REQUIRED, "
              + "LINK_REQUIRED 중 하나입니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "로그인 성공 또는 회원가입/계정 연동 필요 상태 반환"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "유효하지 않거나 만료된 Google ID Token")
  })
  @PostMapping("/complete")
  public ApiResponse<GoogleCompleteResponse> complete(
      @Valid @RequestBody GoogleCompleteRequest request, HttpServletRequest servletRequest) {
    String clientIp = clientIpExtractor.extractClientIp(servletRequest);
    authRateLimitService.checkLoginRateLimit(clientIp);
    return ApiResponse.success(
        SuccessCode.AUTH_GOOGLE_COMPLETE_SUCCESS,
        googleAuthService.complete(request, deviceInfo(servletRequest), clientIp));
  }

  @Operation(
      summary = "Google 신규 회원가입",
      description = "SIGNUP_REQUIRED 응답으로 받은 일회성 토큰과 GACHI 이용약관 동의로 가입합니다.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Google 회원가입 성공")
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

  @Operation(
      summary = "Google 계정 연동",
      description = "기존 GACHI 계정으로 로그인한 사용자가 같은 이메일의 Google 계정을 명시적으로 연동합니다.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Google 계정 연동 성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "403",
        description = "현재 계정과 Google 계정 이메일 불일치"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "이미 Google 계정이 연동됨")
  })
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
