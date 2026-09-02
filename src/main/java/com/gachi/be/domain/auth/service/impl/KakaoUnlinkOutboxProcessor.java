package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.global.exception.ExternalApiException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KakaoUnlinkOutboxProcessor {
  private static final long MAX_RETRY_SECONDS = 300;

  private final KakaoUnlinkOutboxRepository outboxRepository;
  private final SocialAccountRepository socialAccountRepository;
  private final SocialAccountDisconnectService disconnectService;
  private final KakaoClient kakaoClient;

  @Transactional
  public void process(Long eventId) {
    var event = outboxRepository.findByIdForUpdate(eventId).orElse(null);
    OffsetDateTime now = OffsetDateTime.now();
    if (event == null || event.isProcessed() || event.getNextAttemptAt().isAfter(now)) {
      return;
    }

    try {
      kakaoClient.unlink(event.getProviderUserId());
    } catch (ExternalApiException exception) {
      long retrySeconds = Math.min(MAX_RETRY_SECONDS, 1L << Math.min(event.getAttempts() + 1, 8));
      event.scheduleRetry(exception.getMessage(), now.plusSeconds(retrySeconds));
      return;
    }

    socialAccountRepository
        .findByProviderAndProviderUserId(SocialProvider.KAKAO, event.getProviderUserId())
        .ifPresent(disconnectService::disconnect);
    event.complete(now);
  }
}
