package com.gachi.be.domain.notification.api.controller;

import com.gachi.be.domain.notification.dto.request.NotificationReadRequest;
import com.gachi.be.domain.notification.dto.request.PushTokenDeleteRequest;
import com.gachi.be.domain.notification.dto.request.PushTokenRegisterRequest;
import com.gachi.be.domain.notification.dto.response.NotificationListResponse;
import com.gachi.be.domain.notification.dto.response.NotificationReadResponse;
import com.gachi.be.domain.notification.dto.response.NotificationUnreadCountResponse;
import com.gachi.be.domain.notification.dto.response.PushTokenResponse;
import com.gachi.be.domain.notification.service.NotificationService;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 보관함 및 푸시 토큰 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationService notificationService;

  /** 푸시 수신 누락을 복구하기 위해 서버에 저장된 알림 보관함을 최신순으로 조회한다. */
  @Operation(
      summary = "알림 목록 조회",
      description =
          """
          React Native 앱이 푸시를 받지 못한 경우에도 이 API로 서버 보관함을 동기화할 수 있습니다.
          cursor는 이전 응답의 nextCursor를 그대로 전달하고, unreadOnly=true면 미읽음 알림만 조회합니다.
          """)
  @GetMapping
  public ApiResponse<NotificationListResponse> getNotifications(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "다음 페이지 조회용 커서. 이전 응답의 nextCursor") @RequestParam(required = false)
          Long cursor,
      @Parameter(description = "조회 크기. 기본 20, 최대 100")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          Integer size,
      @Parameter(description = "미읽음 알림만 조회할지 여부") @RequestParam(defaultValue = "false")
          boolean unreadOnly) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_LIST_SUCCESS,
        notificationService.getNotifications(userId, cursor, size, unreadOnly));
  }

  @Operation(summary = "미읽음 알림 수 조회", description = "사용자 기준 미읽음 알림 개수를 반환합니다.")
  @GetMapping("/unread-count")
  public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
      @AuthenticationPrincipal Long userId) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_UNREAD_COUNT_SUCCESS, notificationService.getUnreadCount(userId));
  }

  @Operation(summary = "단건 읽음 처리", description = "알림 상세 진입 또는 알림 탭 노출 후 단건 읽음 처리에 사용합니다.")
  @PatchMapping("/{notificationId}/read")
  public ApiResponse<NotificationReadResponse> markRead(
      @AuthenticationPrincipal Long userId, @PathVariable Long notificationId) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_READ_SUCCESS,
        notificationService.markRead(userId, notificationId));
  }

  @Operation(summary = "일괄 읽음 처리", description = "앱이 서버 보관함을 동기화한 뒤 여러 알림을 한 번에 읽음 처리합니다.")
  @PatchMapping("/read")
  public ApiResponse<NotificationReadResponse> markRead(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody NotificationReadRequest request) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_READ_SUCCESS, notificationService.markRead(userId, request));
  }

  @Operation(summary = "전체 읽음 처리", description = "현재 사용자의 미읽음 알림을 모두 읽음 처리합니다.")
  @PatchMapping("/read-all")
  public ApiResponse<NotificationReadResponse> markAllRead(@AuthenticationPrincipal Long userId) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_READ_SUCCESS, notificationService.markAllRead(userId));
  }

  @Operation(
      summary = "푸시 토큰 등록/갱신",
      description =
          """
          RN 앱 시작, 로그인 직후, 토큰 refresh 이벤트에서 호출합니다.
          같은 토큰이 재등록되면 기존 레코드를 활성화하고 플랫폼/디바이스 정보를 갱신합니다.
          """)
  @PostMapping("/tokens")
  public ApiResponse<PushTokenResponse> registerPushToken(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody PushTokenRegisterRequest request) {
    return ApiResponse.success(
        SuccessCode.NOTIFICATION_PUSH_TOKEN_REGISTERED,
        notificationService.registerPushToken(userId, request));
  }

  @Operation(
      summary = "푸시 토큰 삭제",
      description = "로그아웃, 권한 철회, 앱 삭제 전 토큰 정리 시 호출합니다. 이미 삭제된 토큰도 성공으로 처리합니다.")
  @DeleteMapping("/tokens")
  public ApiResponse<Void> deletePushToken(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody PushTokenDeleteRequest request) {
    notificationService.deletePushToken(userId, request);
    return ApiResponse.success(SuccessCode.NOTIFICATION_PUSH_TOKEN_DELETED, null);
  }
}
