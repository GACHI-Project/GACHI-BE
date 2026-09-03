package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.dto.request.KakaoCompleteRequest;
import com.gachi.be.domain.auth.dto.request.KakaoLinkRequest;
import com.gachi.be.domain.auth.dto.request.KakaoSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.KakaoCompleteResponse;
import java.net.URI;

public interface KakaoAuthService {
  URI createAuthorizationUri();

  URI handleCallback(String code, String state);

  KakaoCompleteResponse complete(KakaoCompleteRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse signup(KakaoSignupRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse link(
      Long userId, KakaoLinkRequest request, String deviceInfo, String ipAddress);

  void unlink(Long userId);

  void handleUnlinkWebhook(String authorization, String appId, String providerUserId);
}
