package com.gachi.be.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleCompleteResponse(
    Status status,
    AuthTokenResponse tokens,
    String signupToken,
    String linkToken,
    String email,
    String nickname) {
  public enum Status {
    LOGIN_SUCCESS,
    SIGNUP_REQUIRED,
    LINK_REQUIRED
  }

  public static GoogleCompleteResponse login(AuthTokenResponse tokens) {
    return new GoogleCompleteResponse(Status.LOGIN_SUCCESS, tokens, null, null, null, null);
  }

  public static GoogleCompleteResponse signup(String token, String email, String nickname) {
    return new GoogleCompleteResponse(Status.SIGNUP_REQUIRED, null, token, null, email, nickname);
  }

  public static GoogleCompleteResponse link(String token, String email) {
    return new GoogleCompleteResponse(Status.LINK_REQUIRED, null, null, token, email, null);
  }
}
