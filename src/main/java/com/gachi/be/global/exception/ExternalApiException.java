package com.gachi.be.global.exception;

import com.gachi.be.global.code.ErrorCode;

public class ExternalApiException extends AppException {
  public ExternalApiException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ExternalApiException(ErrorCode errorCode, String detailMessage) {
    super(errorCode, detailMessage);
  }

  public ExternalApiException(ErrorCode errorCode, String detailMessage, Throwable cause) {
    super(errorCode, detailMessage, cause);
  }
}
