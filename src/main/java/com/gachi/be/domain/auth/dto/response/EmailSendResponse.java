package com.gachi.be.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmailSendResponse {
  private long codeTtlSeconds;
  private long resendCooldownSeconds;
}
