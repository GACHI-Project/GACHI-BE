package com.gachi.be.user.api;

import com.gachi.be.global.api.ApiResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.code.SuccessCode;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.exception.ExternalApiException;
import com.gachi.be.user.dto.UserMeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  @GetMapping("/me")
  public ApiResponse<UserMeResponse> getMyInfo() {
    return ApiResponse.success(SuccessCode.OK, new UserMeResponse("api 테스트 중입니다."));
  }

  @GetMapping("/me/error")
  public ApiResponse<Void> getMyInfoError(@RequestParam(defaultValue = "user") String type) {
    if ("external".equalsIgnoreCase(type)) {
      throw new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR);
    }
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
  }
}
