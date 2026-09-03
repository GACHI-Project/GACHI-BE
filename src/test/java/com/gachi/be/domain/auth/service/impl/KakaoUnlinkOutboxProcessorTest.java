package com.gachi.be.domain.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.auth.entity.KakaoUnlinkOutbox;
import com.gachi.be.domain.auth.entity.SocialAccount;
import com.gachi.be.domain.auth.entity.SocialProvider;
import com.gachi.be.domain.auth.repository.KakaoUnlinkOutboxRepository;
import com.gachi.be.domain.auth.repository.SocialAccountRepository;
import com.gachi.be.domain.auth.service.KakaoClient;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.ExternalApiException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KakaoUnlinkOutboxProcessorTest {
  @Mock private KakaoUnlinkOutboxRepository outboxRepository;
  @Mock private SocialAccountRepository socialAccountRepository;
  @Mock private SocialAccountDisconnectService disconnectService;
  @Mock private KakaoClient kakaoClient;

  private KakaoUnlinkOutboxProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new KakaoUnlinkOutboxProcessor(
            outboxRepository, socialAccountRepository, disconnectService, kakaoClient);
  }

  @Test
  void successfulExternalUnlinkFinalizesLocalStateAndOutbox() {
    KakaoUnlinkOutbox event = event();
    SocialAccount account =
        SocialAccount.builder().provider(SocialProvider.KAKAO).providerUserId("kakao-42").build();
    when(outboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
    when(socialAccountRepository.findByUserIdAndProviderForUpdate(42L, SocialProvider.KAKAO))
        .thenReturn(Optional.of(account));

    processor.process(1L);

    verify(kakaoClient).unlink("kakao-42");
    verify(disconnectService).disconnect(account);
    assertThat(event.isProcessed()).isTrue();
  }

  @Test
  void failedExternalUnlinkSchedulesRetryWithoutLocalDisconnect() {
    KakaoUnlinkOutbox event = event();
    SocialAccount account =
        SocialAccount.builder().provider(SocialProvider.KAKAO).providerUserId("kakao-42").build();
    when(outboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
    when(socialAccountRepository.findByUserIdAndProviderForUpdate(42L, SocialProvider.KAKAO))
        .thenReturn(Optional.of(account));
    org.mockito.Mockito.doThrow(new ExternalApiException(ErrorCode.EXTERNAL_API_ERROR))
        .when(kakaoClient)
        .unlink("kakao-42");

    processor.process(1L);

    assertThat(event.getAttempts()).isEqualTo(1);
    assertThat(event.getNextAttemptAt()).isAfter(event.getCreatedAt());
    assertThat(event.isProcessed()).isFalse();
    verify(disconnectService, never()).disconnect(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void staleEventDoesNotUnlinkAccountOwnedByAnotherUser() {
    KakaoUnlinkOutbox event = event();
    when(outboxRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
    when(socialAccountRepository.findByUserIdAndProviderForUpdate(42L, SocialProvider.KAKAO))
        .thenReturn(Optional.empty());

    processor.process(1L);

    verify(kakaoClient, never()).unlink("kakao-42");
    verify(disconnectService, never()).disconnect(org.mockito.ArgumentMatchers.any());
    assertThat(event.isProcessed()).isTrue();
  }

  private KakaoUnlinkOutbox event() {
    KakaoUnlinkOutbox event =
        KakaoUnlinkOutbox.builder().userId(42L).providerUserId("kakao-42").build();
    ReflectionTestUtils.setField(event, "id", 1L);
    ReflectionTestUtils.setField(
        event, "createdAt", java.time.OffsetDateTime.now().minusSeconds(1));
    return event;
  }
}
