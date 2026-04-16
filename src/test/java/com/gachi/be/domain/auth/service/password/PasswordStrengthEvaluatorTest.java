package com.gachi.be.domain.auth.service.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordStrengthEvaluatorTest {

  @Test
  void evaluatesDangerousWhenLengthAndCompositionAreBothLow() {
    assertThat(PasswordStrengthEvaluator.evaluate("Qa1x2w3e"))
        .isEqualTo(PasswordStrength.DANGEROUS);
  }

  @Test
  void evaluatesNormalAtLengthAndCompositionBoundary() {
    assertThat(PasswordStrengthEvaluator.evaluate("Normal12ab")).isEqualTo(PasswordStrength.NORMAL);
  }

  @Test
  void evaluatesSafeWhenLengthAndCompositionReachSafeThreshold() {
    assertThat(PasswordStrengthEvaluator.evaluate("C0mp!exAlpha9"))
        .isEqualTo(PasswordStrength.SAFE);
  }

  @Test
  void evaluatesVerySafeAtLongLengthAndThreeCompositions() {
    assertThat(PasswordStrengthEvaluator.evaluate("T9!mQ2#vL7@rN5$wX"))
        .isEqualTo(PasswordStrength.VERY_SAFE);
  }

  @Test
  void evaluatesDangerousWhenOnlyOneCompositionTypeExists() {
    assertThat(PasswordStrengthEvaluator.evaluate("12345678901234567890"))
        .isEqualTo(PasswordStrength.DANGEROUS);
  }
}
