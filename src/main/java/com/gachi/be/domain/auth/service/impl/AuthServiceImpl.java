package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.request.CheckEmailRequest;
import com.gachi.be.domain.auth.dto.request.CheckLoginIdRequest;
import com.gachi.be.domain.auth.dto.request.CheckPhoneNumberRequest;
import com.gachi.be.domain.auth.dto.request.EmailSendRequest;
import com.gachi.be.domain.auth.dto.request.EmailVerifyRequest;
import com.gachi.be.domain.auth.dto.request.LoginRequest;
import com.gachi.be.domain.auth.dto.request.ReissueRequest;
import com.gachi.be.domain.auth.dto.request.SignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.DuplicateCheckResponse;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.dto.response.SignupResponse;
import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.domain.auth.service.AuthService;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.domain.auth.service.JwtTokenProvider;
import com.gachi.be.domain.auth.service.TokenHashService;
import com.gachi.be.domain.auth.service.password.PasswordStrengthEvaluator;
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
import java.util.regex.Pattern;
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
  private static final int PASSWORD_MIN_LENGTH = 8;
  private static final int PASSWORD_MAX_LENGTH = 20;
  private static final int PASSWORD_MIN_COMPOSITION = 2;
  private static final int PASSWORD_IDENTIFIER_MIN_LENGTH = 3;
  private static final int PASSWORD_MIN_PHONE_CHUNK_LENGTH = 4;
  private static final int PASSWORD_SEQUENCE_LIMIT = 4;
  private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile("[A-Za-z]");
  private static final Pattern PASSWORD_DIGIT_PATTERN = Pattern.compile("[0-9]");
  private static final Pattern PASSWORD_SPECIAL_PATTERN = Pattern.compile("[\\p{P}\\p{S}]");
  private static final Pattern PASSWORD_REPEAT_PATTERN = Pattern.compile("(.)\\1{2,}");
  private static final Pattern PASSWORD_CANONICAL_PATTERN = Pattern.compile("[^a-z0-9]");
  private static final Pattern PASSWORD_NON_DIGIT_PATTERN = Pattern.compile("[^0-9]");

  private final UserRepository userRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final TokenHashService tokenHashService;
  private final EmailVerificationStore emailVerificationStore;
  private final AuthMailService authMailService;
  private final AuthProperties authProperties;

  @Override
  @Transactional(readOnly = true)
  public DuplicateCheckResponse checkLoginId(CheckLoginIdRequest request) {
    String loginId = normalizeText(request.loginId());
    if (userRepository.existsByLoginId(loginId)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_LOGIN_ID);
    }
    return new DuplicateCheckResponse(true);
  }

  @Override
  @Transactional(readOnly = true)
  public DuplicateCheckResponse checkEmail(CheckEmailRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }
    return new DuplicateCheckResponse(true);
  }

  @Override
  @Transactional(readOnly = true)
  public DuplicateCheckResponse checkPhoneNumber(CheckPhoneNumberRequest request) {
    String phoneNumber = normalizePhone(request.phoneNumber());
    if (userRepository.existsByPhoneNumber(phoneNumber)) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
    }
    return new DuplicateCheckResponse(true);
  }

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
    // 프론트 실시간 검증을 우회한 요청도 차단하기 위해 서버에서 정책을 강제한다.
    validatePasswordPolicy(request.password(), loginId, email, phoneNumber);
    enforcePasswordStrength(request.password());
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
      rollbackIssuedCodeSafely(email);
      throw e;
    } catch (Exception e) {
      rollbackIssuedCodeSafely(email);
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

  private void validatePasswordPolicy(
      String password, String loginId, String email, String phoneNumber) {
    if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_LENGTH_INVALID);
    }

    int compositionCount = 0;
    if (PASSWORD_LETTER_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (PASSWORD_DIGIT_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (PASSWORD_SPECIAL_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (compositionCount < PASSWORD_MIN_COMPOSITION) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_COMPOSITION_INVALID);
    }

    if (containsForbiddenPattern(password, loginId, email, phoneNumber)) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_FORBIDDEN_PATTERN);
    }
  }

  /** 강도 판정 결과가 위험이면 회원가입을 차단한다. */
  private void enforcePasswordStrength(String password) {
    if (!PasswordStrengthEvaluator.evaluate(password).canSignup()) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_STRENGTH_DANGEROUS);
    }
  }

  private boolean containsForbiddenPattern(
      String password, String loginId, String email, String phoneNumber) {
    // 계정 식별자/예측 가능한 문자열이 비밀번호에 포함되는 케이스를 묶어서 차단한다.
    String normalizedPassword = password.toLowerCase(Locale.ROOT);
    if (password.chars().anyMatch(Character::isWhitespace)) {
      return true;
    }
    if (PASSWORD_REPEAT_PATTERN.matcher(normalizedPassword).find()) {
      return true;
    }
    if (containsIgnoreCase(normalizedPassword, loginId)) {
      return true;
    }

    String emailLocalPart = email;
    int emailAtIndex = email.indexOf('@');
    if (emailAtIndex > 0) {
      emailLocalPart = email.substring(0, emailAtIndex);
    }
    if (emailLocalPart.length() >= 3 && containsIgnoreCase(normalizedPassword, emailLocalPart)) {
      return true;
    }

    if (containsPhoneChunk(normalizedPassword, phoneNumber)) {
      return true;
    }

    return containsSequentialPattern(normalizedPassword);
  }

  private boolean containsIgnoreCase(String password, String token) {
    String normalizedPassword = normalizeText(password).toLowerCase(Locale.ROOT);
    String normalizedToken = normalizeText(token).toLowerCase(Locale.ROOT);
    if (normalizedToken.length() >= PASSWORD_IDENTIFIER_MIN_LENGTH
        && normalizedPassword.contains(normalizedToken)) {
      return true;
    }

    // '_' '.' '-' 같은 구분자를 제거한 정규형도 비교해서 식별자 우회를 막는다.
    String canonicalToken = canonicalizePasswordToken(normalizedToken);
    String canonicalPassword = canonicalizePasswordToken(normalizedPassword);
    return canonicalToken.length() >= PASSWORD_IDENTIFIER_MIN_LENGTH
        && canonicalPassword.contains(canonicalToken);
  }

  private String canonicalizePasswordToken(String value) {
    return PASSWORD_CANONICAL_PATTERN.matcher(value).replaceAll("");
  }

  private boolean containsPhoneChunk(String password, String phoneNumber) {
    String normalizedPhone = normalizePhone(phoneNumber);
    // 원문 비밀번호는 구분자 삽입으로 우회 가능하므로 숫자만 추출해서 비교한다.
    String digitOnlyPassword = extractDigits(password);
    if (normalizedPhone.length() < PASSWORD_MIN_PHONE_CHUNK_LENGTH) {
      return false;
    }
    for (int start = 0;
        start <= normalizedPhone.length() - PASSWORD_MIN_PHONE_CHUNK_LENGTH;
        start++) {
      String chunk = normalizedPhone.substring(start, start + PASSWORD_MIN_PHONE_CHUNK_LENGTH);
      if (digitOnlyPassword.contains(chunk)) {
        return true;
      }
    }
    return false;
  }

  private String extractDigits(String value) {
    return PASSWORD_NON_DIGIT_PATTERN.matcher(value).replaceAll("");
  }

  private boolean containsSequentialPattern(String password) {
    int ascending = 1;
    int descending = 1;

    for (int i = 1; i < password.length(); i++) {
      char previous = password.charAt(i - 1);
      char current = password.charAt(i);
      if (!isSameSequentialGroup(previous, current)) {
        ascending = 1;
        descending = 1;
        continue;
      }

      int diff = current - previous;
      ascending = diff == 1 ? ascending + 1 : 1;
      descending = diff == -1 ? descending + 1 : 1;
      if (ascending >= PASSWORD_SEQUENCE_LIMIT || descending >= PASSWORD_SEQUENCE_LIMIT) {
        return true;
      }
    }
    return false;
  }

  private boolean isSameSequentialGroup(char previous, char current) {
    return (Character.isDigit(previous) && Character.isDigit(current))
        || (Character.isLetter(previous) && Character.isLetter(current));
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
                log.warn("Failed to consume verified email mark after commit.", e);
              }
            }
          });
      return;
    }
    try {
      emailVerificationStore.consumeVerifiedEmail(email);
    } catch (Exception e) {
      log.warn("Failed to consume verified email mark without tx sync.", e);
    }
  }

  private void rollbackIssuedCodeSafely(String email) {
    try {
      emailVerificationStore.rollbackIssuedCode(email);
    } catch (Exception rollbackException) {
      log.warn("Failed to rollback issued email verification code.", rollbackException);
    }
  }

  private BusinessException mapDuplicateSignupException(DataIntegrityViolationException e) {
    String message = normalizeText(e.getMostSpecificCause().getMessage());
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
