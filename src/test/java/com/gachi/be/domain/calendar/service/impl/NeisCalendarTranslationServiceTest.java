package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.newsletter.pipeline.PapagoTranslateClient;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NeisCalendarTranslationServiceTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PapagoTranslateClient papagoTranslateClient = mock(PapagoTranslateClient.class);
  private final NeisCalendarTranslationService service =
      new NeisCalendarTranslationService(userRepository, papagoTranslateClient);

  @Test
  void translateUsesUserLanguageAndCachesSameText() {
    User user = mock(User.class);
    when(user.getLanguageCode()).thenReturn("US");
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    when(papagoTranslateClient.translate("중식", "US")).thenReturn("Lunch");

    var context = service.contextFor(10L);

    assertThat(service.translate(context, "중식")).isEqualTo("Lunch");
    assertThat(service.translate(context, "중식")).isEqualTo("Lunch");
    verify(papagoTranslateClient, times(1)).translate("중식", "US");
  }

  @Test
  void translateSkipsPapagoForKoreanUser() {
    User user = mock(User.class);
    when(user.getLanguageCode()).thenReturn("KO");
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));

    var context = service.contextFor(10L);

    assertThat(service.translate(context, "현미밥")).isEqualTo("현미밥");
    verifyNoInteractions(papagoTranslateClient);
  }

  @Test
  void translateFallsBackToOriginalWhenPapagoFails() {
    User user = mock(User.class);
    when(user.getLanguageCode()).thenReturn("VI");
    when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    when(papagoTranslateClient.translate("수학", "VI")).thenThrow(new RuntimeException("failed"));

    var context = service.contextFor(10L);

    assertThat(service.translate(context, "수학")).isEqualTo("수학");
  }
}
