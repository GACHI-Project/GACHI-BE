package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;

/** 인증 유스케이스 진입점. */
public interface AuthService {
  SignupResponse signup(SignupRequest request);

  AuthTokenResponse login(LoginRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse reissue(ReissueRequest request, String deviceInfo, String ipAddress);

  EmailSendResponse sendEmailVerificationCode(EmailSendRequest request);

  void verifyEmailCode(EmailVerifyRequest request);
}
