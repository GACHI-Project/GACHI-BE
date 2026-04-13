package com.gachi.be.domain.auth.service.password;

/**
 * 회원가입 비밀번호 보안 강도 등급이다.
 *
 * <p>정책상 {@link #DANGEROUS}는 가입을 차단하고, {@link #NORMAL} 이상만 가입을 허용한다.
 */
public enum PasswordStrength {
  DANGEROUS,
  NORMAL,
  SAFE,
  VERY_SAFE;

  /**
   * 회원가입 허용 여부를 반환한다.
   *
   * @return 위험 등급이 아니면 true
   */
  public boolean canSignup() {
    return this != DANGEROUS;
  }
}
