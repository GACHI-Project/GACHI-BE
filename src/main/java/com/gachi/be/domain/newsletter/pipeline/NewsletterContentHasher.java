package com.gachi.be.domain.newsletter.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** OCR 정제 본문으로 가정통신문 내용 중복 판단용 해시를 생성합니다. */
@Component
public class NewsletterContentHasher {

  private static final int MIN_NORMALIZED_LENGTH = 30;

  public Optional<String> hash(String text) {
    String normalized = normalize(text);
    if (normalized.length() < MIN_NORMALIZED_LENGTH) {
      return Optional.empty();
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return Optional.of(HexFormat.of().formatHex(hashed));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
    }
  }

  private String normalize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    return Normalizer.normalize(text, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]", "");
  }
}
