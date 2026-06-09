package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.SchoolScheduleCalendarResponse;
import com.gachi.be.domain.calendar.service.SchoolScheduleQueryService;
import com.gachi.be.domain.calendar.service.impl.NeisCalendarTranslationService.TranslationContext;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SchoolScheduleQueryServiceImpl implements SchoolScheduleQueryService {
  private static final long MAX_SCHOOL_SCHEDULE_RANGE_DAYS = 366L;
  private static final Set<String> EXCLUDED_EVENT_NAMES = Set.of("토요휴업일", "토요공휴일", "토요휴무일");
  private static final Set<String> COMMON_HOLIDAY_KEYWORDS =
      Set.of(
          "공휴일", "대체공휴일", "대체휴일", "어린이날", "삼일절", "3·1절", "3.1절", "현충일", "광복절", "개천절", "한글날", "성탄절",
          "석가탄신일", "부처님오신날", "신정", "설날", "추석");

  private final SchoolScheduleChildReader schoolScheduleChildReader;
  private final NeisSchoolScheduleClient neisSchoolScheduleClient;
  private final NeisCalendarTranslationService translationService;

  @Override
  public SchoolScheduleCalendarResponse getSchoolSchedules(
      Long userId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    Map<SchoolIdentity, List<SchoolScheduleChild>> childrenBySchool =
        groupBySchoolIdentity(schoolScheduleChildReader.findChildren(userId));
    TranslationContext translationContext = translationService.contextFor(userId);
    List<SchoolScheduleCalendarResponse.SchoolScheduleGroup> groups = new ArrayList<>();
    Map<ScheduleIdentity, SchoolScheduleCalendarResponse.ScheduleItem> commonHolidays =
        new LinkedHashMap<>();
    for (Map.Entry<SchoolIdentity, List<SchoolScheduleChild>> entry : childrenBySchool.entrySet()) {
      SchoolIdentity identity = entry.getKey();
      List<SchoolScheduleChild> schoolChildren = entry.getValue();
      List<NeisSchoolScheduleItem> schedules =
          neisSchoolScheduleClient.search(
              identity.officeCode(), identity.schoolCode(), fromDate, toDate);
      List<NeisSchoolScheduleItem> schoolSpecificSchedules = new ArrayList<>();
      for (NeisSchoolScheduleItem schedule : schedules) {
        if (isExcludedEvent(schedule)) {
          continue;
        }
        if (isCommonHoliday(schedule)) {
          SchoolScheduleCalendarResponse.ScheduleItem scheduleItem =
              toScheduleItem(schedule, translationContext);
          commonHolidays.putIfAbsent(
              new ScheduleIdentity(
                  schedule.date().format(DateTimeFormatter.ISO_LOCAL_DATE),
                  normalizeText(schedule.eventName())),
              scheduleItem);
          continue;
        }
        if (appliesToAnyChildGrade(schedule, schoolChildren)) {
          schoolSpecificSchedules.add(schedule);
        }
      }
      groups.add(toGroup(identity, schoolChildren, schoolSpecificSchedules, translationContext));
    }

    return new SchoolScheduleCalendarResponse(new ArrayList<>(commonHolidays.values()), groups);
  }

  private void validateRange(LocalDate fromDate, LocalDate toDate) {
    if (toDate.isBefore(fromDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "종료일은 시작일보다 빠를 수 없습니다.");
    }
    long requestedDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    if (requestedDays > MAX_SCHOOL_SCHEDULE_RANGE_DAYS) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "최대 1년까지 조회 가능합니다.");
    }
  }

  private Map<SchoolIdentity, List<SchoolScheduleChild>> groupBySchoolIdentity(
      List<SchoolScheduleChild> children) {
    Map<SchoolIdentity, List<SchoolScheduleChild>> grouped = new LinkedHashMap<>();
    for (SchoolScheduleChild child : children) {
      if (!StringUtils.hasText(child.officeCode()) || !StringUtils.hasText(child.schoolCode())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "자녀 학교의 교육청 코드와 학교 코드가 필요합니다.");
      }
      SchoolIdentity identity = new SchoolIdentity(child.officeCode(), child.schoolCode());
      grouped.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(child);
    }
    return grouped;
  }

  private SchoolScheduleCalendarResponse.SchoolScheduleGroup toGroup(
      SchoolIdentity identity,
      List<SchoolScheduleChild> children,
      List<NeisSchoolScheduleItem> schedules,
      TranslationContext translationContext) {
    List<Long> childIds = children.stream().map(SchoolScheduleChild::childId).toList();
    List<SchoolScheduleCalendarResponse.ChildItem> childItems =
        children.stream()
            .map(
                child ->
                    new SchoolScheduleCalendarResponse.ChildItem(
                        child.childId(), child.childName(), child.grade(), child.colorCode()))
            .toList();
    List<SchoolScheduleCalendarResponse.ScheduleItem> scheduleItems =
        schedules.stream().map(item -> toScheduleItem(item, translationContext)).toList();

    return new SchoolScheduleCalendarResponse.SchoolScheduleGroup(
        identity.groupKey(),
        identity.officeCode(),
        identity.schoolCode(),
        children.get(0).schoolName(),
        childIds,
        childItems,
        scheduleItems);
  }

  private SchoolScheduleCalendarResponse.ScheduleItem toScheduleItem(
      NeisSchoolScheduleItem item, TranslationContext translationContext) {
    NeisSchoolScheduleItem.GradeEventYn gradeEventYn = item.gradeEventYn();
    return new SchoolScheduleCalendarResponse.ScheduleItem(
        item.date().format(DateTimeFormatter.ISO_LOCAL_DATE),
        item.academicYear(),
        translationService.translate(translationContext, item.eventName()),
        translationService.translate(translationContext, item.eventContent()),
        new SchoolScheduleCalendarResponse.GradeEventYn(
            gradeEventYn.grade1(),
            gradeEventYn.grade2(),
            gradeEventYn.grade3(),
            gradeEventYn.grade4(),
            gradeEventYn.grade5(),
            gradeEventYn.grade6()));
  }

  private boolean isExcludedEvent(NeisSchoolScheduleItem item) {
    return EXCLUDED_EVENT_NAMES.contains(normalizeText(item.eventName()));
  }

  private boolean isCommonHoliday(NeisSchoolScheduleItem item) {
    String eventName = normalizeText(item.eventName());
    String eventContent = normalizeText(item.eventContent());
    return COMMON_HOLIDAY_KEYWORDS.stream()
        .anyMatch(keyword -> eventName.contains(keyword) || eventContent.contains(keyword));
  }

  private boolean appliesToAnyChildGrade(
      NeisSchoolScheduleItem item, List<SchoolScheduleChild> children) {
    Set<Integer> childGrades = new LinkedHashSet<>();
    for (SchoolScheduleChild child : children) {
      if (child.grade() != null) {
        childGrades.add(child.grade());
      }
    }
    if (childGrades.isEmpty()) {
      return true;
    }
    return childGrades.stream().anyMatch(grade -> isGradeTarget(item.gradeEventYn(), grade));
  }

  private boolean isGradeTarget(NeisSchoolScheduleItem.GradeEventYn gradeEventYn, int grade) {
    if (gradeEventYn == null) {
      return true;
    }
    return switch (grade) {
      case 1 -> isYes(gradeEventYn.grade1());
      case 2 -> isYes(gradeEventYn.grade2());
      case 3 -> isYes(gradeEventYn.grade3());
      case 4 -> isYes(gradeEventYn.grade4());
      case 5 -> isYes(gradeEventYn.grade5());
      case 6 -> isYes(gradeEventYn.grade6());
      default -> false;
    };
  }

  private boolean isYes(String value) {
    return "Y".equalsIgnoreCase(normalizeText(value));
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.replaceAll("\\s+", "").trim();
  }

  private record SchoolIdentity(String officeCode, String schoolCode) {
    String groupKey() {
      return officeCode + ":" + schoolCode;
    }
  }

  private record ScheduleIdentity(String date, String normalizedEventName) {}
}
