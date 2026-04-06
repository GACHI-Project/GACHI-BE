package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;
import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.domain.auth.service.AuthService;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.auth.service.TokenHashService;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.AppException;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.exception.ExternalApiException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/** 회원가입/로그인/토큰재발급/이메일인증 유스케이스를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
  private final UserRepository userRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final TokenHashService tokenHashService;
  private final EmailVerificationStore emailVerificationStore;
  private final AuthMailService authMailService;
  private final AuthProperties authProperties;

  @Override
  @Transactional
  public SignupResponse signup(SignupRequest request) {
    String email = normalizeEmail(request.email());
    String loginId = normalizeText(request.loginId());
    String name = normalizeText(request.name());
    String phoneNumber = normalizePhone(request.phoneNumber());

    if (!request.password().equals(request.passwordConfirm())) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_CONFIRM_MISMATCH);
    }
    if (!Boolean.TRUE.equals(request.consentAgreed())) {
      throw new BusinessException(ErrorCode.AUTH_CONSENT_REQUIRED);
    }
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }
    if (userRepository.existsByLoginId(loginId)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
    }
    if (userRepository.existsByPhoneNumber(phoneNumber)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
    }
    if (!emailVerificationStore.isEmailVerified(email)) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }

    OffsetDateTime now = OffsetDateTime.now();
    User user =
        User.builder()
            .email(email)
            .loginId(loginId)
            .passwordHash(passwordEncoder.encode(request.password()))
            .name(name)
            .phoneNumber(phoneNumber)
            .status(UserStatus.ACTIVE)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion(authProperties.getConsentVersion())
            .passwordUpdatedAt(now)
            .build();

    User savedUser;
    try {
      savedUser = userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw mapDuplicateSignupException(e);
    }
    consumeVerifiedEmailAfterCommit(email);
    return new SignupResponse(
        savedUser.getId(),
        savedUser.getLoginId(),
        savedUser.getEmail(),
        savedUser.getName(),
        savedUser.getPhoneNumber());
  }

  @Override
  @Transactional
  public AuthTokenResponse login(LoginRequest request, String deviceInfo, String ipAddress) {
    String loginId = normalizeText(request.loginId());

    User user =
        userRepository
            .findByLoginId(loginId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    boolean rememberMe = Boolean.TRUE.equals(request.rememberMe());
    return issueTokens(
        user, rememberMe, normalizeNullable(deviceInfo), normalizeNullable(ipAddress));
  }

  @Override
  @Transactional
  public AuthTokenResponse reissue(ReissueRequest request, String deviceInfo, String ipAddress) {
    String refreshToken = normalizeText(request.refreshToken());
    JwtTokenProvider.RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
    String tokenHash = tokenHashService.sha256(refreshToken);

    AuthRefreshToken existingToken =
        authRefreshTokenRepository
            .findByJtiAndTokenHash(claims.getJti(), tokenHash)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));

    if (existingToken.getRevokedAt() != null) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_REVOKED);
    }
    if (existingToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
      throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
    }

    User user = existingToken.getUser();
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }

    existingToken.revoke();
    String nextDeviceInfo = mergeNullable(deviceInfo, existingToken.getDeviceInfo());
    String nextIpAddress = mergeNullable(ipAddress, existingToken.getIpAddress());
    return issueTokens(user, existingToken.isRememberMe(), nextDeviceInfo, nextIpAddress);
  }

  @Override
  @Transactional
  public EmailSendResponse sendEmailVerificationCode(EmailSendRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }

    String code = emailVerificationStore.issueCode(email);
    try {
      authMailService.sendVerificationCode(email, code);
    } catch (AppException e) {
      emailVerificationStore.rollbackIssuedCode(email);
      throw e;
    } catch (Exception e) {
      emailVerificationStore.rollbackIssuedCode(email);
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to send verification code.");
    }

    return new EmailSendResponse(
        authProperties.getEmail().getCodeTtlSeconds(),
        authProperties.getEmail().getResendCooldownSeconds());
  }

  @Override
  @Transactional
  public void verifyEmailCode(EmailVerifyRequest request) {
    emailVerificationStore.verifyCode(
        normalizeEmail(request.email()), normalizeText(request.code()));
  }

  /** 로그인/재발급 공통 토큰 발급 + refresh token 세션 저장 로직. */
  private AuthTokenResponse issueTokens(
      User user, boolean rememberMe, String deviceInfo, String ipAddress) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime refreshExpiresAt =
        now.plusDays(
            rememberMe
                ? authProperties.getJwt().getRefreshTokenRememberDays()
                : authProperties.getJwt().getRefreshTokenDays());

    JwtTokenProvider.JwtToken accessToken = jwtTokenProvider.issueAccessToken(user);
    String jti = UUID.randomUUID().toString();
    JwtTokenProvider.JwtToken refreshToken =
        jwtTokenProvider.issueRefreshToken(user, jti, refreshExpiresAt);

    AuthRefreshToken refreshTokenEntity =
        AuthRefreshToken.builder()
            .user(user)
            .tokenHash(tokenHashService.sha256(refreshToken.getToken()))
            .jti(jti)
            .deviceInfo(deviceInfo)
            .ipAddress(ipAddress)
            .rememberMe(rememberMe)
            .expiresAt(refreshToken.getExpiresAt())
            .lastUsedAt(now)
            .build();
    authRefreshTokenRepository.save(refreshTokenEntity);

    return new AuthTokenResponse(
        "Bearer",
        accessToken.getToken(),
        refreshToken.getToken(),
        accessToken.getExpiresAt(),
        refreshToken.getExpiresAt(),
        rememberMe);
  }

  private String normalizeEmail(String email) {
    return normalizeText(email).toLowerCase(Locale.ROOT);
  }

  private String normalizePhone(String phoneNumber) {
    return normalizeText(phoneNumber).replaceAll("[^0-9]", "");
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }

  private String normalizeNullable(String value) {
    String trimmed = normalizeText(value);
    return StringUtils.hasText(trimmed) ? trimmed : null;
  }

  private String mergeNullable(String latest, String fallback) {
    String normalizedLatest = normalizeNullable(latest);
    return StringUtils.hasText(normalizedLatest) ? normalizedLatest : normalizeNullable(fallback);
  }

  private void consumeVerifiedEmailAfterCommit(String email) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                emailVerificationStore.consumeVerifiedEmail(email);
              } catch (Exception e) {
                // 가입 자체는 커밋된 상태이므로 후처리 실패만 별도로 로깅하고 요청은 실패시키지 않는다.
                log.warn("Failed to consume verified email mark after commit. email={}", email, e);
              }
            }
          });
      return;
    }
    try {
      emailVerificationStore.consumeVerifiedEmail(email);
    } catch (Exception e) {
      log.warn("Failed to consume verified email mark without tx sync. email={}", email, e);
    }
  }

  private BusinessException mapDuplicateSignupException(DataIntegrityViolationException e) {
    String message = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
    String normalizedMessage = message.toLowerCase(Locale.ROOT);
    if (normalizedMessage.contains("users_email_key")) {
      return new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }
    if (normalizedMessage.contains("uk_users_login_id")) {
      return new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
    }
    if (normalizedMessage.contains("uk_users_phone_number")) {
      return new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
    }
    return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
  }
}
