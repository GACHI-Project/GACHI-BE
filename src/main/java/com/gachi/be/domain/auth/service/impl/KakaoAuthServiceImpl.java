package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.dto.request.KakaoCompleteRequest;
import com.gachi.be.domain.auth.dto.request.KakaoLinkRequest;
import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.KakaoCompleteResponse;
import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.AuthTokenIssuer;
import com.gachi.be.domain.auth.service.KakaoAuthService;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.domain.auth.service.KakaoLoginStore;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.entity.enums.NotificationPreference;
import com.gachi.be.domain.user.entity.enums.UserStatus;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthServiceImpl implements KakaoAuthService {
  private static final String TICKET = "ticket";
  private static final String SIGNUP = "signup";
  private static final String LINK = "link";

  private final AuthProperties authProperties;
  private final KakaoClient kakaoClient;
  private final KakaoLoginStore kakaoLoginStore;
  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;
  private final AuthTokenIssuer authTokenIssuer;
  private final KakaoUnlinkOutboxRepository kakaoUnlinkOutboxRepository;
  private final SocialAccountDisconnectService disconnectService;

  @Override
  public URI createAuthorizationUri() {
    AuthProperties.Kakao kakao = enabledProperties();
    String state = kakaoLoginStore.issueState(Duration.ofSeconds(kakao.stateTtlSeconds()));
    return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
        .queryParam("client_id", kakao.restApiKey())
        .queryParam("redirect_uri", kakao.redirectUri())
        .queryParam("response_type", "code")
        .queryParam("state", state)
        .build()
        .encode()
        .toUri();
  }

  @Override
  public URI handleCallback(String code, String state) {
    AuthProperties.Kakao kakao = enabledProperties();
    if (!StringUtils.hasText(code) || !kakaoLoginStore.consumeState(state)) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_STATE_INVALID);
    }
    KakaoClient.KakaoIdentity identity = kakaoClient.authenticate(code.trim());
    requireVerifiedEmail(identity);
    String ticket =
        kakaoLoginStore.issue(TICKET, identity, Duration.ofSeconds(kakao.ticketTtlSeconds()));
    return UriComponentsBuilder.fromUriString(kakao.appRedirectUri())
        .queryParam("ticket", ticket)
        .build()
        .encode()
        .toUri();
  }

  @Override
  @Transactional
  public KakaoCompleteResponse complete(
      KakaoCompleteRequest request, String deviceInfo, String ipAddress) {
    AuthProperties.Kakao kakao = enabledProperties();
    KakaoClient.KakaoIdentity identity = kakaoLoginStore.consume(TICKET, request.ticket());
    requireVerifiedEmail(identity);

    return socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.KAKAO, identity.providerUserId())
        .map(
            account -> {
              ensureActive(account.getUser());
              return KakaoCompleteResponse.login(
                  authTokenIssuer.issue(
                      account.getUser(),
                      Boolean.TRUE.equals(request.rememberMe()),
                      normalizeNullable(deviceInfo),
                      normalizeNullable(ipAddress)));
            })
        .orElseGet(
            () -> {
              if (userRepository.existsByEmail(normalizeEmail(identity.email()))) {
                String linkToken =
                    kakaoLoginStore.issue(
                        LINK, identity, Duration.ofSeconds(kakao.signupTokenTtlSeconds()));
                return KakaoCompleteResponse.link(linkToken, normalizeEmail(identity.email()));
              }
              String signupToken =
                  kakaoLoginStore.issue(
                      SIGNUP, identity, Duration.ofSeconds(kakao.signupTokenTtlSeconds()));
              return KakaoCompleteResponse.signup(
                  signupToken,
                  normalizeEmail(identity.email()),
                  normalizedName(identity.nickname()));
            });
  }

  @Override
  @Transactional
  public AuthTokenResponse signup(KakaoSignupRequest request, String deviceInfo, String ipAddress) {
    enabledProperties();
    if (!Boolean.TRUE.equals(request.consentAgreed())) {
      throw new BusinessException(ErrorCode.AUTH_CONSENT_REQUIRED);
    }
    KakaoClient.KakaoIdentity identity = kakaoLoginStore.consume(SIGNUP, request.signupToken());
    requireVerifiedEmail(identity);
    String email = normalizeEmail(identity.email());
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_LINK_REQUIRED);
    }

    OffsetDateTime now = OffsetDateTime.now();
    String phoneNumber = normalizePhone(identity.phoneNumber());
    if (StringUtils.hasText(phoneNumber) && userRepository.existsByPhoneNumber(phoneNumber)) {
      phoneNumber = null;
    }
    User user =
        User.builder()
            .email(email)
            .name(normalizedName(identity.nickname()))
            .phoneNumber(phoneNumber)
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
              .provider(SocialProvider.KAKAO)
              .providerUserId(identity.providerUserId())
              .build());
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_ALREADY_LINKED);
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
      Long userId, KakaoLinkRequest request, String deviceInfo, String ipAddress) {
    enabledProperties();
    KakaoClient.KakaoIdentity identity = kakaoLoginStore.consume(LINK, request.linkToken());
    requireVerifiedEmail(identity);
    User user =
        userRepository
            .findByIdWithLock(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID));
    ensureActive(user);
    if (!normalizeEmail(identity.email()).equals(normalizeEmail(user.getEmail()))) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_LINK_EMAIL_MISMATCH);
    }
    if (socialAccountRepository.findByUserIdAndProvider(userId, SocialProvider.KAKAO).isPresent()
        || socialAccountRepository
            .findByProviderAndProviderUserId(SocialProvider.KAKAO, identity.providerUserId())
            .isPresent()) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_ALREADY_LINKED);
    }
    try {
      socialAccountRepository.saveAndFlush(
          SocialAccount.builder()
              .user(user)
              .provider(SocialProvider.KAKAO)
              .providerUserId(identity.providerUserId())
              .build());
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_ALREADY_LINKED);
    }
    return authTokenIssuer.issue(
        user,
        Boolean.TRUE.equals(request.rememberMe()),
        normalizeNullable(deviceInfo),
        normalizeNullable(ipAddress));
  }

  @Override
  @Transactional
  public void unlink(Long userId) {
    enabledProperties();
    socialAccountRepository
        .findByUserIdAndProviderForUpdate(userId, SocialProvider.KAKAO)
        .ifPresent(
            account -> {
              account.requestDisconnect();
              if (!kakaoUnlinkOutboxRepository.existsByProviderUserIdAndProcessedAtIsNull(
                  account.getProviderUserId())) {
                kakaoUnlinkOutboxRepository.save(
                    KakaoUnlinkOutbox.builder()
                        .userId(userId)
                        .providerUserId(account.getProviderUserId())
                        .build());
              }
            });
  }

  @Override
  @Transactional
  public void handleUnlinkWebhook(String authorization, String appId, String providerUserId) {
    AuthProperties.Kakao kakao = authProperties.getKakao();
    String expectedAuthorization = "KakaoAK " + kakao.adminKey();
    if (!kakao.enabled()
        || !secureEquals(expectedAuthorization, authorization)
        || !secureEquals(kakao.appId(), appId)
        || !StringUtils.hasText(providerUserId)) {
      log.warn("Rejected invalid Kakao unlink webhook.");
      return;
    }
    socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.KAKAO, providerUserId)
        .ifPresent(disconnectService::disconnect);
    kakaoUnlinkOutboxRepository
        .findByProviderUserIdAndProcessedAtIsNull(providerUserId)
        .ifPresent(event -> event.complete(OffsetDateTime.now()));
  }

  private AuthProperties.Kakao enabledProperties() {
    AuthProperties.Kakao kakao = authProperties.getKakao();
    if (!kakao.enabled()) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_NOT_CONFIGURED);
    }
    return kakao;
  }

  private void requireVerifiedEmail(KakaoClient.KakaoIdentity identity) {
    if (identity == null
        || !StringUtils.hasText(identity.providerUserId())
        || !StringUtils.hasText(identity.email())) {
      throw new BusinessException(ErrorCode.AUTH_KAKAO_EMAIL_REQUIRED);
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
      return "카카오 사용자";
    }
    return normalized.length() <= 50 ? normalized : normalized.substring(0, 50);
  }

  private String normalizePhone(String phoneNumber) {
    if (!StringUtils.hasText(phoneNumber)) {
      return null;
    }
    String digits = phoneNumber.replaceAll("[^0-9]", "");
    if (phoneNumber.trim().startsWith("+82") && digits.startsWith("82")) {
      digits = "0" + digits.substring(2);
    }
    return digits.length() <= 20 ? digits : null;
  }

  private String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private boolean secureEquals(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }
}
