package com.gachi.be.domain.user.api.controller;

import com.gachi.be.domain.auth.service.AuthenticatedUserResolver;
import com.gachi.be.domain.user.dto.response.UserMeResponse;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.SuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자 기준 내 정보 조회 API를 제공한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
  private final AuthenticatedUserResolver authenticatedUserResolver;

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
            user.getPhoneNumber()));
  }
}
