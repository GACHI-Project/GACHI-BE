package com.gachi.be.domain.auth.dto.request;

public final class PhoneNumberValidation {
  public static final String REGEXP = "^[0-9]{3}-?[0-9]{3,4}-?[0-9]{4}$";
  public static final String MESSAGE = "전화번호는 숫자만 입력하거나 하이픈(-)을 포함해 주세요.";

  private PhoneNumberValidation() {}
}
