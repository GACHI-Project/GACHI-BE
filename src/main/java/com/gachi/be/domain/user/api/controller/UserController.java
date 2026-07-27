package com.gachi.be.domain.user.api.controller;

import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.service.AuthRateLimitService;
import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.auth.service.ClientIpExtractor;
import com.gachi.be.domain.user.dto.request.ChangeLanguageRequest;
import com.gachi.be.domain.user.dto.request.ChangeNotificationRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeCodeSendRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeVerifyRequest;
import com.gachi.be.domain.user.dto.request.PasswordChangeRequest;
import com.gachi.be.domain.user.dto.request.ProfileUpdateRequest;
import com.gachi.be.domain.user.dto.request.UserWithdrawalRequest;
import com.gachi.be.domain.user.dto.response.EmailChangeResponse;
import com.gachi.be.domain.user.dto.response.ProfileUpdateResponse;
import com.gachi.be.domain.user.dto.response.UserMeResponse;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.service.UserProfileService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 로그인 사용자 기준 내 정보 조회 API를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final AuthenticatedUserResolver authenticatedUserResolver;
  private final AuthRateLimitService authRateLimitService;
  private final ClientIpExtractor clientIpExtractor;
  private final UserProfileService userProfileService;

  @Operation(
      summary = "사용자 내 정보 조회",
      description =
          """
         마이페이지에서 사용자의 정보를 볼 수 있습니다. 이름, 닉네임, 등록일을 반환하고
         추후 이메일을 변경할 경우를 고려 이메일도 반환합니다.
         알림설정 여부와 사용자의 언어까지 조회합니다.
         """)
  @GetMapping("/me")
  public ApiResponse<UserMeResponse> getMyInfo(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return ApiResponse.success(
        SuccessCode.OK,
        new UserMeResponse(
            user.getId(),
            user.getLoginId(),
            user.getEmail(),
            user.getName(),
            user.getLanguageCode(),
            user.getPhoneNumber(),
            user.isNotificationEnabled(),
            user.getNotificationPreference(),
            user.getCreatedAt()));
  }

  /** 언어 설정 변경 API */
  @Operation(
      summary = "사용자 언어 설정 변경",
      description =
          """
        마이페이지에서 내가 회원가입 시에 설정했떤 언어를 변경할 수 있습니다. 해당 언어를 변경한 뒤에 스캔된 문서들은 전부 해당 언어로 번역됩니다.
        """)
  @PatchMapping("/me/language")
  public ApiResponse<Void> changeLanguage(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid ChangeLanguageRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    userProfileService.changeLanguage(user, request);
    return ApiResponse.success(SuccessCode.USER_LANGUAGE_UPDATED, null);
  }

  @Operation(summary = "사용자 알림 설정 변경", description = "마이페이지에서 알림 수신 단계를 변경합니다.")
  @PatchMapping("/me/notification")
  public ApiResponse<Void> changeNotificationPreference(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid ChangeNotificationRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    userProfileService.changeNotificationPreference(user, request);
    return ApiResponse.success(SuccessCode.USER_NOTIFICATION_UPDATED, null);
  }

  @Operation(summary = "프로필 이름/전화번호 변경", description = "마이페이지에서 이름과 전화번호를 수정합니다.")
  @PatchMapping("/me/profile")
  public ApiResponse<ProfileUpdateResponse> updateProfile(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid ProfileUpdateRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return ApiResponse.success(
        SuccessCode.USER_PROFILE_UPDATED, userProfileService.updateProfile(user, request));
  }

  @Operation(summary = "이메일 변경 인증번호 발송", description = "현재 비밀번호 확인 후 새 이메일로 인증번호를 발송합니다.")
  @PostMapping("/me/email/send")
  public ApiResponse<EmailSendResponse> sendEmailChangeCode(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid EmailChangeCodeSendRequest request,
      HttpServletRequest servletRequest) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    authRateLimitService.checkProfileEmailChangeSendRateLimit(
        clientIpExtractor.extractClientIp(servletRequest), request.email());
    return ApiResponse.success(
        SuccessCode.USER_EMAIL_CHANGE_CODE_SENT,
        userProfileService.sendEmailChangeCode(user, request));
  }

  @Operation(summary = "이메일 변경 인증번호 검증", description = "새 이메일로 받은 인증번호를 검증합니다.")
  @PostMapping("/me/email/verify")
  public ApiResponse<Void> verifyEmailChangeCode(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid EmailChangeVerifyRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    userProfileService.verifyEmailChangeCode(user, request);
    return ApiResponse.success(SuccessCode.USER_EMAIL_CHANGE_VERIFIED, null);
  }

  @Operation(summary = "이메일 변경", description = "인증 완료된 새 이메일로 계정 이메일을 변경합니다.")
  @PatchMapping("/me/email")
  public ApiResponse<EmailChangeResponse> changeEmail(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid EmailChangeRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    return ApiResponse.success(
        SuccessCode.USER_EMAIL_UPDATED, userProfileService.changeEmail(user, request));
  }

  @Operation(summary = "비밀번호 변경", description = "마이페이지에서 현재 비밀번호 확인 후 새 비밀번호로 변경합니다.")
  @PatchMapping("/me/password")
  public ApiResponse<Void> changePassword(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid PasswordChangeRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    userProfileService.changePassword(user, request);
    return ApiResponse.success(SuccessCode.USER_PASSWORD_UPDATED, null);
  }

  @Operation(summary = "회원 탈퇴", description = "현재 비밀번호를 확인한 후 계정을 탈퇴 처리합니다.")
  @DeleteMapping("/me")
  public ApiResponse<Void> withdraw(
      @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
      @RequestBody @Valid UserWithdrawalRequest request) {
    User user = authenticatedUserResolver.resolveActiveUser(authorizationHeader);
    userProfileService.withdraw(user, request);
    return ApiResponse.success(SuccessCode.USER_WITHDRAWAL_SUCCESS, null);
  }
}
