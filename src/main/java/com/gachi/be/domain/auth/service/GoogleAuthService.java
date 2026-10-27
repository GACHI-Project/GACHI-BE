package com.gachi.be.domain.auth.service;

import com.gachi.be.domain.auth.dto.request.GoogleCompleteRequest;
import com.gachi.be.domain.auth.dto.request.GoogleLinkRequest;
import com.gachi.be.domain.auth.dto.request.GoogleSignupRequest;
import com.gachi.be.domain.auth.dto.response.AuthTokenResponse;
import com.gachi.be.domain.auth.dto.response.GoogleCompleteResponse;

public interface GoogleAuthService {
  GoogleCompleteResponse complete(
      GoogleCompleteRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse signup(GoogleSignupRequest request, String deviceInfo, String ipAddress);

  AuthTokenResponse link(
      Long userId, GoogleLinkRequest request, String deviceInfo, String ipAddress);
}
