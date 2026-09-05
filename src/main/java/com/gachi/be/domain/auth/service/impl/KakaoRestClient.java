package com.gachi.be.domain.auth.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gachi.be.domain.auth.config.AuthProperties;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.ExternalApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoRestClient implements KakaoClient {
  private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
  private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";
  private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

  private final AuthProperties authProperties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public KakaoRestClient(
      AuthProperties authProperties,
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper) {
    this.authProperties = authProperties;
    this.objectMapper = objectMapper;
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = restClientBuilder.requestFactory(requestFactory).build();
  }

  @Override
  public KakaoIdentity authenticate(String authorizationCode) {
    ensureEnabled();
    try {
      TokenResponse token = requestToken(authorizationCode);
      String accessToken = Objects.requireNonNull(Objects.requireNonNull(token).accessToken());
      UserResponse user = requestUser(accessToken);
      Long userId = Objects.requireNonNull(Objects.requireNonNull(user).id());
      KakaoAccount account = user.account();
      if (account == null
          || !Boolean.TRUE.equals(account.emailValid())
          || !Boolean.TRUE.equals(account.emailVerified())) {
        return new KakaoIdentity(String.valueOf(userId), null, nickname(account), null);
      }
      return new KakaoIdentity(
          String.valueOf(userId), account.email(), nickname(account), account.phoneNumber());
    } catch (RestClientException | NullPointerException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Kakao authentication request failed.", e);
    }
  }

  @Override
  public void unlink(String providerUserId) {
    ensureEnabled();
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("target_id_type", "user_id");
    body.add("target_id", providerUserId);
    try {
      restClient
          .post()
          .uri(UNLINK_URL)
          .header("Authorization", "KakaoAK " + authProperties.getKakao().adminKey())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      if (isAlreadyUnlinked(e)) {
        return;
      }
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Kakao unlink request failed.", e);
    } catch (RestClientException e) {
      throw new ExternalApiException(
          ErrorCode.EXTERNAL_API_ERROR, "Kakao unlink request failed.", e);
    }
  }

  private boolean isAlreadyUnlinked(RestClientResponseException exception) {
    try {
      return exception.getStatusCode().value() == 400
          && objectMapper.readTree(exception.getResponseBodyAsString()).path("code").asInt()
              == -101;
    } catch (Exception exceptionWhileReadingBody) {
      return false;
    }
  }

  private TokenResponse requestToken(String authorizationCode) {
    AuthProperties.Kakao kakao = authProperties.getKakao();
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("client_id", kakao.restApiKey());
    body.add("redirect_uri", kakao.redirectUri());
    body.add("code", authorizationCode);
    body.add("client_secret", kakao.clientSecret());
    return restClient
        .post()
        .uri(TOKEN_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .body(TokenResponse.class);
  }

  private UserResponse requestUser(String accessToken) {
    return restClient
        .get()
        .uri(USER_URL)
        .header("Authorization", "Bearer " + accessToken)
        .retrieve()
        .body(UserResponse.class);
  }

  private String nickname(KakaoAccount account) {
    return account != null && account.profile() != null ? account.profile().nickname() : null;
  }

  private void ensureEnabled() {
    if (!authProperties.getKakao().enabled()) {
      throw new ExternalApiException(ErrorCode.AUTH_KAKAO_NOT_CONFIGURED);
    }
  }

  private record TokenResponse(@JsonProperty("access_token") String accessToken) {}

  private record UserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount account) {}

  private record KakaoAccount(
      String email,
      @JsonProperty("is_email_valid") Boolean emailValid,
      @JsonProperty("is_email_verified") Boolean emailVerified,
      Profile profile,
      @JsonProperty("phone_number") String phoneNumber) {}

  private record Profile(String nickname) {}
}
