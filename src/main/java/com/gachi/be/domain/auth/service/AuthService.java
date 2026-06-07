package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.dto.request.CheckEmailRequest;
import com.gachi.be.domain.auth.dto.request.CheckLoginIdRequest;
import com.gachi.be.domain.auth.dto.request.CheckPhoneNumberRequest;
import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.FindLoginIdEmailSendRequest;
import com.gachi.be.domain.auth.dto.request.FindLoginIdEmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.LogoutRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetEmailSendRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetEmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.PasswordResetRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.DuplicateCheckResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.FindLoginIdResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;

/** 인증 유스케이스 진입점. */
public interface AuthService {
  DuplicateCheckResponse checkLoginId(CheckLoginIdRequest request);

  DuplicateCheckResponse checkEmail(CheckEmailRequest request);

  DuplicateCheckResponse checkPhoneNumber(CheckPhoneNumberRequest request);

  SignupResponse signup(SignupRequest request);

  AuthTokenResponse login(LoginRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse reissue(ReissueRequest request, String deviceInfo, String ipAddress);

  void logout(LogoutRequest request);

  EmailSendResponse sendEmailVerificationCode(EmailSendRequest request);

  void verifyEmailCode(EmailVerifyRequest request);

  EmailSendResponse sendFindLoginIdEmailVerificationCode(FindLoginIdEmailSendRequest request);

  FindLoginIdResponse verifyFindLoginIdEmailCode(FindLoginIdEmailVerifyRequest request);

  EmailSendResponse sendPasswordResetEmailVerificationCode(PasswordResetEmailSendRequest request);

  void verifyPasswordResetEmailCode(PasswordResetEmailVerifyRequest request);

  void resetPassword(PasswordResetRequest request);
}
