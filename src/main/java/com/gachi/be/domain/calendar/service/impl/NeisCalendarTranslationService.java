package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.newsletter.pipeline.PapagoTranslateClient;
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
class NeisCalendarTranslationService {
  private static final String DEFAULT_LANGUAGE = "KO";

  private final UserRepository userRepository;
  private final PapagoTranslateClient papagoTranslateClient;

  /**
   * NEIS 캘린더 응답 번역에 사용할 사용자 언어와 요청 단위 번역 캐시를 만든다.
   *
   * <p>같은 급식명/행사명/수업명이 반복될 수 있어, 한 요청 안에서는 동일 문구를 한 번만 번역한다.
   */
  TranslationContext contextFor(Long userId) {
    String language =
        userRepository
            .findById(userId)
            .map(User::getLanguageCode)
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toUpperCase())
            .orElse(DEFAULT_LANGUAGE);
    return new TranslationContext(language, new HashMap<>());
  }

  String translate(TranslationContext context, String text) {
    if (context == null || !context.shouldTranslate() || !StringUtils.hasText(text)) {
      return text;
    }
    return context
        .cache()
        .computeIfAbsent(text, value -> translateOrOriginal(value, context.language()));
  }

  private String translateOrOriginal(String text, String language) {
    try {
      String translated = papagoTranslateClient.translate(text, language);
      return StringUtils.hasText(translated) ? translated : text;
    } catch (RuntimeException e) {
      // 외부 번역 실패가 급식/시간표/학사일정 조회 실패로 전파되지 않도록 원문을 유지한다.
      log.warn("[NEIS Calendar] 파파고 번역 실패로 원문을 사용합니다. language={}", language, e);
      return text;
    }
  }

  record TranslationContext(String language, Map<String, String> cache) {
    boolean shouldTranslate() {
      return StringUtils.hasText(language) && !DEFAULT_LANGUAGE.equalsIgnoreCase(language);
    }
  }
}
