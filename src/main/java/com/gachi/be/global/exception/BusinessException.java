package com.gachi.be.global.exception;

import com.gachi.be.global.code.ErrorCode;

public class BusinessException extends AppException {
  public BusinessException(ErrorCode errorCode) {
    super(errorCode);
  }

  public BusinessException(ErrorCode errorCode, String detailMessage) {
    super(errorCode, detailMessage);
  }
}
