package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.config.GoogleAuthProperties;
import com.gachi.be.domain.auth.dto.request.GoogleCompleteRequest;
import com.gachi.be.domain.auth.dto.request.GoogleLinkRequest;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.GoogleCompleteResponse;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.GoogleAuthService;
import com.gachi.be.domain.auth.service.GoogleClient;
import com.gachi.be.domain.auth.service.GoogleLoginStore;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GoogleAuthServiceImpl implements GoogleAuthService {
  private static final String SIGNUP = "signup";
  private static final String LINK = "link";

  private final AuthProperties authProperties;
  private final GoogleAuthProperties googleAuthProperties;
  private final GoogleClient googleClient;
  private final GoogleLoginStore googleLoginStore;
  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;
  private final AuthTokenIssuer authTokenIssuer;

  @Override
  @Transactional
  public GoogleCompleteResponse complete(
      GoogleCompleteRequest request, String deviceInfo, String ipAddress) {
    GoogleAuthProperties google = enabledProperties();
    GoogleClient.GoogleIdentity identity = googleClient.verify(request.idToken());
    requireVerifiedEmail(identity);

    return socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.GOOGLE, identity.providerUserId())
        .map(
            account -> {
              ensureActive(account.getUser());
              return GoogleCompleteResponse.login(
                  authTokenIssuer.issue(
                      account.getUser(),
                      Boolean.TRUE.equals(request.rememberMe()),
                      normalizeNullable(deviceInfo),
                      normalizeNullable(ipAddress)));
            })
        .orElseGet(
            () -> {
              String email = normalizeEmail(identity.email());
              Duration ttl = Duration.ofSeconds(google.signupTokenTtlSeconds());
              Optional<User> existingUser = userRepository.findByEmail(email);
              if (existingUser.isPresent()) {
                ensureActive(existingUser.get());
                return GoogleCompleteResponse.link(
                    googleLoginStore.issue(LINK, identity, ttl), email);
              }
              return GoogleCompleteResponse.signup(
                  googleLoginStore.issue(SIGNUP, identity, ttl),
                  email,
                  normalizedName(identity.nickname()));
            });
  }

  @Override
  @Transactional
  public AuthTokenResponse signup(
      GoogleSignupRequest request, String deviceInfo, String ipAddress) {
    enabledProperties();
    if (!Boolean.TRUE.equals(request.consentAgreed())) {
      throw new BusinessException(ErrorCode.AUTH_CONSENT_REQUIRED);
    }
    GoogleClient.GoogleIdentity identity = googleLoginStore.consume(SIGNUP, request.signupToken());
    requireVerifiedEmail(identity);
    String email = normalizeEmail(identity.email());
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent()) {
      ensureActive(existingUser.get());
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_LINK_REQUIRED);
    }

    OffsetDateTime now = OffsetDateTime.now();
    User user =
        User.builder()
            .email(email)
            .name(normalizedName(identity.nickname()))
            .status(UserStatus.ACTIVE)
            .languageCode(request.languageCode())
            .notificationPreference(
                request.notificationPreference() != null
                    ? request.notificationPreference()
                    : NotificationPreference.IMPORTANT)
            .emailVerifiedAt(now)
            .consentAgreedAt(now)
            .consentVersion(authProperties.getConsentVersion())
            .passwordUpdatedAt(now)
            .passwordChangeRequired(false)
            .build();
    try {
      userRepository.saveAndFlush(user);
      socialAccountRepository.saveAndFlush(
          SocialAccount.builder()
              .user(user)
              .provider(SocialProvider.GOOGLE)
              .providerUserId(identity.providerUserId())
              .build());
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_ALREADY_LINKED);
    }
    return authTokenIssuer.issue(
        user,
        Boolean.TRUE.equals(request.rememberMe()),
        normalizeNullable(deviceInfo),
        normalizeNullable(ipAddress));
  }

  @Override
  @Transactional
  public AuthTokenResponse link(
      Long userId, GoogleLinkRequest request, String deviceInfo, String ipAddress) {
    enabledProperties();
    GoogleClient.GoogleIdentity identity = googleLoginStore.consume(LINK, request.linkToken());
    requireVerifiedEmail(identity);
    User user =
        userRepository
            .findByIdWithLock(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID));
    ensureActive(user);
    if (!normalizeEmail(identity.email()).equals(normalizeEmail(user.getEmail()))) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_LINK_EMAIL_MISMATCH);
    }
    if (socialAccountRepository.findByUserIdAndProvider(userId, SocialProvider.GOOGLE).isPresent()
        || socialAccountRepository
            .findByProviderAndProviderUserId(SocialProvider.GOOGLE, identity.providerUserId())
            .isPresent()) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_ALREADY_LINKED);
    }
    try {
      socialAccountRepository.saveAndFlush(
          SocialAccount.builder()
              .user(user)
              .provider(SocialProvider.GOOGLE)
              .providerUserId(identity.providerUserId())
              .build());
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_ALREADY_LINKED);
    }
    return authTokenIssuer.issue(
        user,
        Boolean.TRUE.equals(request.rememberMe()),
        normalizeNullable(deviceInfo),
        normalizeNullable(ipAddress));
  }

  private GoogleAuthProperties enabledProperties() {
    if (!googleAuthProperties.enabled()) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_NOT_CONFIGURED);
    }
    return googleAuthProperties;
  }

  private void requireVerifiedEmail(GoogleClient.GoogleIdentity identity) {
    if (identity == null
        || !StringUtils.hasText(identity.providerUserId())
        || !StringUtils.hasText(identity.email())
        || !identity.emailVerified()) {
      throw new BusinessException(ErrorCode.AUTH_GOOGLE_EMAIL_REQUIRED);
    }
  }

  private void ensureActive(User user) {
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.AUTH_ACCOUNT_WITHDRAWN);
    }
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizedName(String nickname) {
    String normalized = normalizeNullable(nickname);
    if (!StringUtils.hasText(normalized)) {
      return "Google 사용자";
    }
    return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
  }

  private String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
