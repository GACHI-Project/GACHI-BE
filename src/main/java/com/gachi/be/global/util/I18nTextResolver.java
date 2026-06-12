package com.gachi.be.global.util;

import java.util.List;
import java.util.Map;

public final class I18nTextResolver {

  public static final String DEFAULT_LANGUAGE = "KO";

  public static final List<String> SUPPORTED_LANGUAGES = List.of("KO", "US", "ZH", "VI");

  private I18nTextResolver() {}

  public static String resolve(Map<String, String> values, String language, String fallback) {
    if (values == null || values.isEmpty()) {
      return fallback;
    }
    String text = values.get(language);
    if (text != null && !text.isBlank()) {
      return text;
    }
    text = values.get(DEFAULT_LANGUAGE);
    if (text != null && !text.isBlank()) {
      return text;
    }
    return fallback;
  }
}
