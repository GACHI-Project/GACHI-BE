package com.gachi.be.domain.auth.service.impl;

import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoUnlinkOutboxScheduler {
  private final KakaoUnlinkOutboxRepository outboxRepository;
  private final KakaoUnlinkOutboxProcessor processor;

  @Scheduled(fixedDelayString = "${app.auth.kakao.unlink-outbox-poll-ms:5000}")
  public void processPending() {
    outboxRepository
        .findTop20ByProcessedAtIsNullAndNextAttemptAtLessThanEqualOrderByIdAsc(OffsetDateTime.now())
        .forEach(event -> processor.process(event.getId()));
  }
}
