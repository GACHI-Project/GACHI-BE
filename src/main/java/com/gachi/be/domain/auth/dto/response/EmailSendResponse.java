package com.gachi.be.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailSendResponse {
  private final long codeTtlSeconds;
  private final long resendCooldownSeconds;
  private final String verificationCode;

  public EmailSendResponse(long codeTtlSeconds, long resendCooldownSeconds) {
    this(codeTtlSeconds, resendCooldownSeconds, null);
  }

  public EmailSendResponse(
      long codeTtlSeconds, long resendCooldownSeconds, String verificationCode) {
    this.codeTtlSeconds = codeTtlSeconds;
    this.resendCooldownSeconds = resendCooldownSeconds;
    this.verificationCode = verificationCode;
  }
}
