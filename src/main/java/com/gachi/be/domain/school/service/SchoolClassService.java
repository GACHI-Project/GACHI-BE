package com.gachi.be.domain.school.service;

import com.gachi.be.domain.school.client.NeisSchoolClassClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolClassItem;
import com.gachi.be.domain.school.dto.response.SchoolClassItem;
import com.gachi.be.domain.school.dto.response.SchoolClassResponse;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 학교 선택 직후 반 선택 UI가 사용할 학급 목록을 조회한다. */
@Service
@RequiredArgsConstructor
public class SchoolClassService {
  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private static final Duration CACHE_TTL = Duration.ofHours(24);

  private final NeisSchoolClassClient neisSchoolClassClient;
  private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

  public SchoolClassResponse search(
      String officeCode, String schoolCode, String academicYear, Integer grade) {
    String normalizedOfficeCode = requireText(officeCode, "교육청 코드는 필수입니다.");
    String normalizedSchoolCode = requireText(schoolCode, "학교 코드는 필수입니다.");
    String resolvedAcademicYear =
        StringUtils.hasText(academicYear) ? academicYear.trim() : currentAcademicYear();
    validateAcademicYear(resolvedAcademicYear);
    validateGrade(grade);

    CacheKey key =
        new CacheKey(normalizedOfficeCode, normalizedSchoolCode, resolvedAcademicYear, grade);
    Instant now = Instant.now();
    CacheEntry entry =
        cache.compute(
            key,
            (ignored, existing) -> {
              if (existing != null && !existing.isExpired(now)) {
                return existing;
              }
              List<SchoolClassItem> classes =
                  normalize(
                      neisSchoolClassClient.search(
                          normalizedOfficeCode, normalizedSchoolCode, resolvedAcademicYear, grade));
              SchoolClassResponse response =
                  new SchoolClassResponse(
                      normalizedOfficeCode,
                      normalizedSchoolCode,
                      resolvedAcademicYear,
                      grade,
                      classes.size(),
                      classes);
              return new CacheEntry(response, now.plus(CACHE_TTL));
            });
    return entry.response();
  }

  private List<SchoolClassItem> normalize(List<NeisSchoolClassItem> items) {
    Map<ClassKey, SchoolClassItem> distinct = new LinkedHashMap<>();
    for (NeisSchoolClassItem item : items) {
      if (item.grade() == null || !StringUtils.hasText(item.className())) {
        continue;
      }
      String className = item.className().trim();
      ClassKey key = new ClassKey(item.grade(), className);
      distinct.putIfAbsent(key, new SchoolClassItem(item.academicYear(), item.grade(), className));
    }
    return distinct.values().stream()
        .sorted(
            Comparator.comparing(SchoolClassItem::grade)
                .thenComparing(item -> classNameSortValue(item.className()))
                .thenComparing(SchoolClassItem::className))
        .toList();
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, message);
    }
    return value.trim();
  }

  private void validateAcademicYear(String academicYear) {
    if (!academicYear.matches("\\d{4}")) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "학년도는 YYYY 형식이어야 합니다.");
    }
  }

  private void validateGrade(Integer grade) {
    if (grade != null && (grade < 1 || grade > 6)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "초등학교 학년은 1~6학년만 조회할 수 있습니다.");
    }
  }

  private String currentAcademicYear() {
    LocalDate today = LocalDate.now(KOREA_ZONE);
    int year = today.getMonthValue() < 3 ? today.getYear() - 1 : today.getYear();
    return String.valueOf(year);
  }

  private int classNameSortValue(String className) {
    try {
      return Integer.parseInt(className);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }

  private record CacheKey(
      String officeCode, String schoolCode, String academicYear, Integer grade) {}

  private record ClassKey(Integer grade, String className) {}

  private record CacheEntry(SchoolClassResponse response, Instant expiresAt) {
    boolean isExpired(Instant now) {
      return now.isAfter(expiresAt);
    }
  }
}
