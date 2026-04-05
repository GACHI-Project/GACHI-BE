package com.gachi.be.domain.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SignupResponse {
  private Long userId;
  private String loginId;
  private String email;
  private String name;
  private String phoneNumber;
}
