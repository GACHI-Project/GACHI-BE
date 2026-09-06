package com.gachi.be.domain.user.service;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.response.EmailSendResponse;
import com.gachi.be.domain.auth.entity.AuthRefreshToken;
import com.gachi.be.domain.auth.repository.AuthRefreshTokenRepository;
import com.gachi.be.domain.auth.service.AuthMailService;
import com.gachi.be.domain.auth.service.EmailVerificationPurpose;
import com.gachi.be.domain.auth.service.EmailVerificationStore;
import com.gachi.be.domain.auth.service.impl.NoopAuthMailService;
import com.gachi.be.domain.auth.service.password.PasswordPolicyValidator;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.PushDeviceToken;
import com.gachi.be.domain.notification.repository.PushDeviceTokenRepository;
import com.gachi.be.domain.user.dto.request.ChangeLanguageRequest;
import com.gachi.be.domain.user.dto.request.ChangeNotificationRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeCodeSendRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeRequest;
import com.gachi.be.domain.user.dto.request.EmailChangeVerifyRequest;
import com.gachi.be.domain.user.dto.request.PasswordChangeRequest;
import com.gachi.be.domain.user.dto.request.ProfileUpdateRequest;
import com.gachi.be.domain.user.dto.request.UserWithdrawalRequest;
import com.gachi.be.domain.user.dto.response.EmailChangeResponse;
import com.gachi.be.domain.user.dto.response.ProfileUpdateResponse;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.AppException;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.exception.ExternalApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {
  private final UserRepository userRepository;
  private final AuthRefreshTokenRepository authRefreshTokenRepository;
  private final NewsletterRepository newsletterRepository;
  private final PushDeviceTokenRepository pushDeviceTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuthMailService authMailService;
  private final EmailVerificationStore emailVerificationStore;
  private final AuthProperties authProperties;
  private final PasswordPolicyValidator passwordPolicyValidator;

  @Transactional
  public void changeLanguage(User user, ChangeLanguageRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    String previousLanguage = currentUser.getLanguageCode();
    String newLanguage = request.languageCode();
    if (Objects.equals(previousLanguage, newLanguage)) {
      return;
    }

    currentUser.updateLanguage(newLanguage);
    int cancelledCount =
        newsletterRepository.cancelInProgressByUserId(
            currentUser.getId(),
            List.of(NewsletterStatus.PENDING, NewsletterStatus.PROCESSING),
            NewsletterStatus.FAILED,
            newLanguage);
    log.info(
        "[Language] 언어 설정 변경. userId={}, {} -> {}, cancelledPipelines={}",
        currentUser.getId(),
        previousLanguage,
        newLanguage,
        cancelledCount);
  }

  @Transactional
  public void changeNotificationPreference(User user, ChangeNotificationRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    currentUser.updateNotificationPreference(request.notificationPreference());
  }

  @Transactional
  public ProfileUpdateResponse updateProfile(User user, ProfileUpdateRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    String name = normalizeText(request.name());
    String phoneNumber = normalizePhone(request.phoneNumber());

    if (userRepository.existsByPhoneNumberAndIdNot(phoneNumber, currentUser.getId())) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
    }

    currentUser.updateProfile(name, phoneNumber);
    try {
      userRepository.saveAndFlush(currentUser);
    } catch (DataIntegrityViolationException e) {
      if (isUniqueConstraintViolation(e, "uk_users_phone_number")) {
        throw new BusinessException(ErrorCode.AUTH_DUPLICATE_PHONE_NUMBER);
      }
      throw e;
    }
    return new ProfileUpdateResponse(currentUser.getName(), currentUser.getPhoneNumber());
  }

  @Transactional
  public void changePassword(User user, PasswordChangeRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    if (!request.newPassword().equals(request.newPasswordConfirm())) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_CONFIRM_MISMATCH);
    }

    passwordPolicyValidator.validate(
        request.newPassword(),
        currentUser.getLoginId(),
        currentUser.getEmail(),
        currentUser.getPhoneNumber());
    currentUser.resetPassword(passwordEncoder.encode(request.newPassword()), OffsetDateTime.now());
    revokeActiveRefreshTokens(currentUser.getId());
  }

  @Transactional
  public void withdraw(User user, UserWithdrawalRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    currentUser.withdraw(OffsetDateTime.now());
    revokeActiveRefreshTokens(currentUser.getId());
    pushDeviceTokenRepository
        .findAllByUserIdAndEnabledTrueAndDeletedAtIsNull(currentUser.getId())
        .forEach(PushDeviceToken::softDelete);
  }

  @Transactional
  public EmailSendResponse sendEmailChangeCode(User user, EmailChangeCodeSendRequest request) {
    User currentUser = findActiveUser(user.getId());
    String email = normalizeEmail(request.email());
    if (!passwordEncoder.matches(request.currentPassword(), currentUser.getPasswordHash())) {
      throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    if (userRepository.existsByEmailAndIdNot(email, currentUser.getId())) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }

    String verificationSubject = emailChangeVerificationSubject(currentUser.getId(), email);
    String code =
        emailVerificationStore.issueCode(
            verificationSubject, EmailVerificationPurpose.CHANGE_EMAIL);
    try {
      authMailService.sendVerificationCode(email, code);
    } catch (AppException e) {
      rollbackIssuedEmailChangeCodeSafely(verificationSubject);
      throw e;
    } catch (Exception e) {
      rollbackIssuedEmailChangeCodeSafely(verificationSubject);
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Failed to send email change verification code.", e);
    }

    return new EmailSendResponse(
        authProperties.getEmail().getCodeTtlSeconds(),
        authProperties.getEmail().getResendCooldownSeconds(),
        shouldExposeVerificationCodeForLocalTest() ? code : null);
  }

  @Transactional
  public void verifyEmailChangeCode(User user, EmailChangeVerifyRequest request) {
    User currentUser = findActiveUser(user.getId());
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmailAndIdNot(email, currentUser.getId())) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }
    emailVerificationStore.verifyCode(
        emailChangeVerificationSubject(currentUser.getId(), email),
        normalizeText(request.code()),
        EmailVerificationPurpose.CHANGE_EMAIL);
  }

  @Transactional
  public EmailChangeResponse changeEmail(User user, EmailChangeRequest request) {
    User currentUser = findActiveUserWithLock(user.getId());
    String email = normalizeEmail(request.email());
    String verificationSubject = emailChangeVerificationSubject(currentUser.getId(), email);
    if (!emailVerificationStore.isEmailVerified(
        verificationSubject, EmailVerificationPurpose.CHANGE_EMAIL)) {
      throw new BusinessException(ErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }
    if (userRepository.existsByEmailAndIdNot(email, currentUser.getId())) {
      throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
    }

    currentUser.changeEmail(email, OffsetDateTime.now());
    try {
      userRepository.saveAndFlush(currentUser);
    } catch (DataIntegrityViolationException e) {
      if (isUniqueConstraintViolation(e, "users_email_key")) {
        throw new BusinessException(ErrorCode.AUTH_DUPLICATE_EMAIL);
      }
      throw e;
    }
    revokeActiveRefreshTokens(currentUser.getId());
    consumeVerifiedEmailAfterCommit(verificationSubject);
    return new EmailChangeResponse(currentUser.getEmail());
  }

  private User findActiveUser(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }
    return user;
  }

  private User findActiveUserWithLock(Long userId) {
    User user =
        userRepository
            .findByIdWithLock(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }
    return user;
  }

  private String emailChangeVerificationSubject(Long userId, String email) {
    return userId + ":" + email;
  }

  private void revokeActiveRefreshTokens(Long userId) {
    authRefreshTokenRepository
        .findAllByUserIdAndRevokedAtIsNull(userId)
        .forEach(AuthRefreshToken::revoke);
  }

  private void consumeVerifiedEmailAfterCommit(String verificationSubject) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                emailVerificationStore.consumeVerifiedEmail(
                    verificationSubject, EmailVerificationPurpose.CHANGE_EMAIL);
              } catch (Exception e) {
                log.warn("Failed to consume email change verification mark after commit.", e);
              }
            }
          });
      return;
    }
    try {
      emailVerificationStore.consumeVerifiedEmail(
          verificationSubject, EmailVerificationPurpose.CHANGE_EMAIL);
    } catch (Exception e) {
      log.warn("Failed to consume email change verification mark without tx sync.", e);
    }
  }

  private void rollbackIssuedEmailChangeCodeSafely(String verificationSubject) {
    try {
      emailVerificationStore.rollbackIssuedCode(
          verificationSubject, EmailVerificationPurpose.CHANGE_EMAIL);
    } catch (Exception rollbackException) {
      log.warn("Failed to rollback issued email change verification code.", rollbackException);
    }
  }

  private boolean isUniqueConstraintViolation(
      DataIntegrityViolationException exception, String constraintName) {
    Throwable cause = exception.getMostSpecificCause();
    String message = cause != null ? normalizeText(cause.getMessage()) : "";
    return message.toLowerCase(Locale.ROOT).contains(constraintName);
  }

  /**
   * 로컬 개발/테스트에서 실제 메일이 나가지 않는 경우에만 응답에 인증 코드를 노출한다.
   *
   * <p>프로덕션에서는 AuthConfig가 noop 메일 발송기 생성을 차단한다.
   */
  private boolean shouldExposeVerificationCodeForLocalTest() {
    return authMailService instanceof NoopAuthMailService
        && AuthProperties.Email.STORE_TYPE_MEMORY.equalsIgnoreCase(
            authProperties.getEmail().getStore());
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
}
