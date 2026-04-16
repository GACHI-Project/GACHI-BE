package com.gachi.be.global.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.code.SuccessCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  private final boolean isSuccess;
  private final String status;
  private final String code;
  private final String message;
  private final T result;

  public static <T> ApiResponse<T> success(SuccessCode successCode, T result) {
    return ApiResponse.<T>builder()
        .isSuccess(true)
        .status(successCode.getHttpStatus().name())
        .code(successCode.getCode())
        .message(successCode.getMessage())
        .result(result)
        .build();
  }

  public static ApiResponse<Void> fail(ErrorCode errorCode) {
    return ApiResponse.<Void>builder()
        .isSuccess(false)
        .status(errorCode.getHttpStatus().name())
        .code(errorCode.getCode())
        .message(errorCode.getMessage())
        .build();
  }

  public static <T> ApiResponse<T> fail(ErrorCode errorCode, T result) {
    return ApiResponse.<T>builder()
        .isSuccess(false)
        .status(errorCode.getHttpStatus().name())
        .code(errorCode.getCode())
        .message(errorCode.getMessage())
        .result(result)
        .build();
  }
}
