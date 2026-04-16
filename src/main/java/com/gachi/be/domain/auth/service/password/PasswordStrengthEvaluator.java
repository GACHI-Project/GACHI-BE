package com.gachi.be.domain.auth.service.password;

import java.util.regex.Pattern;

/**
 * 기존 비밀번호 정책을 통과한 문자열을 4단계 보안 강도로 분류한다.
 *
 * <p>이 평가는 가입 허용 여부를 최종 결정하는 백엔드 단일 기준으로 사용한다.
 */
public final class PasswordStrengthEvaluator {
  private static final int NORMAL_LENGTH_THRESHOLD = 10;
  private static final int SAFE_LENGTH_THRESHOLD = 12;
  private static final int VERY_SAFE_LENGTH_THRESHOLD = 16;

  private static final Pattern LETTER_PATTERN = Pattern.compile("[A-Za-z]");
  private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
  private static final Pattern SPECIAL_PATTERN = Pattern.compile("[\\p{P}\\p{S}]");

  private PasswordStrengthEvaluator() {}

  /**
   * 비밀번호를 위험/보통/안전/매우 안전으로 판정한다.
   *
   * <p>점수 기준:
   *
   * <ul>
   *   <li>길이 점수: 10자 이상 +1, 12자 이상 +2, 16자 이상 +3
   *   <li>문자 조합 점수: 1종 0점, 2종 +1, 3종 +2 (영문/숫자/특수문자)
   * </ul>
   *
   * <p>최종 등급:
   *
   * <ul>
   *   <li>0~1: 위험
   *   <li>2~3: 보통
   *   <li>4: 안전
   *   <li>5 이상: 매우 안전
   * </ul>
   */
  public static PasswordStrength evaluate(String password) {
    int compositionScore = scoreByComposition(password);
    if (compositionScore == 0) {
      return PasswordStrength.DANGEROUS;
    }

    int score = scoreByLength(password) + compositionScore;
    if (score <= 1) {
      return PasswordStrength.DANGEROUS;
    }
    if (score <= 3) {
      return PasswordStrength.NORMAL;
    }
    if (score == 4) {
      return PasswordStrength.SAFE;
    }
    return PasswordStrength.VERY_SAFE;
  }

  private static int scoreByLength(String password) {
    int length = password.length();
    if (length >= VERY_SAFE_LENGTH_THRESHOLD) {
      return 3;
    }
    if (length >= SAFE_LENGTH_THRESHOLD) {
      return 2;
    }
    if (length >= NORMAL_LENGTH_THRESHOLD) {
      return 1;
    }
    return 0;
  }

  private static int scoreByComposition(String password) {
    int compositionCount = 0;
    if (LETTER_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (DIGIT_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (SPECIAL_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (compositionCount >= 3) {
      return 2;
    }
    if (compositionCount == 2) {
      return 1;
    }
    return 0;
  }
}
