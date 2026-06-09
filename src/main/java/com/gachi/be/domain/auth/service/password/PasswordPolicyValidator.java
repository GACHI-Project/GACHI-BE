package com.gachi.be.domain.auth.service.password;

import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 회원가입, 비밀번호 재설정, 마이페이지 비밀번호 변경에서 같은 비밀번호 정책을 검증한다. */
@Component
public class PasswordPolicyValidator {
  private static final int PASSWORD_MIN_LENGTH = 8;
  private static final int PASSWORD_MAX_LENGTH = 20;
  private static final int PASSWORD_MIN_COMPOSITION = 2;
  private static final int PASSWORD_IDENTIFIER_MIN_LENGTH = 3;
  private static final int PASSWORD_MIN_PHONE_CHUNK_LENGTH = 4;
  private static final int PASSWORD_SEQUENCE_LIMIT = 4;
  private static final Pattern PASSWORD_LETTER_PATTERN = Pattern.compile("[A-Za-z]");
  private static final Pattern PASSWORD_DIGIT_PATTERN = Pattern.compile("[0-9]");
  private static final Pattern PASSWORD_SPECIAL_PATTERN = Pattern.compile("[\\p{P}\\p{S}]");
  private static final Pattern PASSWORD_REPEAT_PATTERN = Pattern.compile("(.)\\1{2,}");
  private static final Pattern PASSWORD_CANONICAL_PATTERN = Pattern.compile("[^a-z0-9]");
  private static final Pattern PASSWORD_NON_DIGIT_PATTERN = Pattern.compile("[^0-9]");

  public void validate(String password, String loginId, String email, String phoneNumber) {
    validatePasswordPolicy(password, loginId, email, phoneNumber);
    enforcePasswordStrength(password);
  }

  private void validatePasswordPolicy(
      String password, String loginId, String email, String phoneNumber) {
    if (password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_LENGTH_INVALID);
    }

    int compositionCount = 0;
    if (PASSWORD_LETTER_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (PASSWORD_DIGIT_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (PASSWORD_SPECIAL_PATTERN.matcher(password).find()) {
      compositionCount++;
    }
    if (compositionCount < PASSWORD_MIN_COMPOSITION) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_COMPOSITION_INVALID);
    }

    if (containsForbiddenPattern(password, loginId, email, phoneNumber)) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_POLICY_FORBIDDEN_PATTERN);
    }
  }

  private void enforcePasswordStrength(String password) {
    if (!PasswordStrengthEvaluator.evaluate(password).canSignup()) {
      throw new BusinessException(ErrorCode.AUTH_PASSWORD_STRENGTH_DANGEROUS);
    }
  }

  private boolean containsForbiddenPattern(
      String password, String loginId, String email, String phoneNumber) {
    String normalizedPassword = password.toLowerCase(Locale.ROOT);
    if (password.chars().anyMatch(Character::isWhitespace)) {
      return true;
    }
    if (PASSWORD_REPEAT_PATTERN.matcher(normalizedPassword).find()) {
      return true;
    }
    if (containsIgnoreCase(normalizedPassword, loginId)) {
      return true;
    }

    String emailLocalPart = email;
    int emailAtIndex = email.indexOf('@');
    if (emailAtIndex > 0) {
      emailLocalPart = email.substring(0, emailAtIndex);
    }
    if (emailLocalPart.length() >= 3 && containsIgnoreCase(normalizedPassword, emailLocalPart)) {
      return true;
    }

    if (containsPhoneChunk(normalizedPassword, phoneNumber)) {
      return true;
    }

    return containsSequentialPattern(normalizedPassword);
  }

  private boolean containsIgnoreCase(String password, String token) {
    String normalizedPassword = normalizeText(password).toLowerCase(Locale.ROOT);
    String normalizedToken = normalizeText(token).toLowerCase(Locale.ROOT);
    if (normalizedToken.length() >= PASSWORD_IDENTIFIER_MIN_LENGTH
        && normalizedPassword.contains(normalizedToken)) {
      return true;
    }

    String canonicalToken = canonicalizePasswordToken(normalizedToken);
    String canonicalPassword = canonicalizePasswordToken(normalizedPassword);
    return canonicalToken.length() >= PASSWORD_IDENTIFIER_MIN_LENGTH
        && canonicalPassword.contains(canonicalToken);
  }

  private String canonicalizePasswordToken(String value) {
    return PASSWORD_CANONICAL_PATTERN.matcher(value).replaceAll("");
  }

  private boolean containsPhoneChunk(String password, String phoneNumber) {
    String normalizedPhone = normalizePhone(phoneNumber);
    String digitOnlyPassword = extractDigits(password);
    if (normalizedPhone.length() < PASSWORD_MIN_PHONE_CHUNK_LENGTH) {
      return false;
    }
    for (int start = 0;
        start <= normalizedPhone.length() - PASSWORD_MIN_PHONE_CHUNK_LENGTH;
        start++) {
      String chunk = normalizedPhone.substring(start, start + PASSWORD_MIN_PHONE_CHUNK_LENGTH);
      if (digitOnlyPassword.contains(chunk)) {
        return true;
      }
    }
    return false;
  }

  private String extractDigits(String value) {
    return PASSWORD_NON_DIGIT_PATTERN.matcher(value).replaceAll("");
  }

  private boolean containsSequentialPattern(String password) {
    int ascending = 1;
    int descending = 1;

    for (int i = 1; i < password.length(); i++) {
      char previous = password.charAt(i - 1);
      char current = password.charAt(i);
      if (!isSameSequentialGroup(previous, current)) {
        ascending = 1;
        descending = 1;
        continue;
      }

      int diff = current - previous;
      ascending = diff == 1 ? ascending + 1 : 1;
      descending = diff == -1 ? descending + 1 : 1;
      if (ascending >= PASSWORD_SEQUENCE_LIMIT || descending >= PASSWORD_SEQUENCE_LIMIT) {
        return true;
      }
    }
    return false;
  }

  private boolean isSameSequentialGroup(char previous, char current) {
    return (Character.isDigit(previous) && Character.isDigit(current))
        || (Character.isLetter(previous) && Character.isLetter(current));
  }

  private String normalizePhone(String phoneNumber) {
    return normalizeText(phoneNumber).replaceAll("[^0-9]", "");
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }
}
