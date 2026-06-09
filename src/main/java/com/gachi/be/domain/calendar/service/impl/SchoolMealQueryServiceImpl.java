package com.gachi.be.domain.calendar.service.impl;

import com.gachi.be.domain.calendar.dto.response.SchoolMealCalendarResponse;
import com.gachi.be.domain.calendar.service.SchoolMealQueryService;
import com.gachi.be.domain.calendar.service.impl.NeisCalendarTranslationService.TranslationContext;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolMealClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolMealItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SchoolMealQueryServiceImpl implements SchoolMealQueryService {
  private static final long MAX_SCHOOL_MEAL_RANGE_DAYS = 366L;

  private final SchoolScheduleChildReader schoolScheduleChildReader;
  private final NeisSchoolMealClient neisSchoolMealClient;
  private final NeisCalendarTranslationService translationService;

  @Override
  public SchoolMealCalendarResponse getSchoolMeals(
      Long userId, LocalDate fromDate, LocalDate toDate) {
    validateRange(fromDate, toDate);

    Map<SchoolIdentity, List<SchoolScheduleChild>> childrenBySchool =
        groupBySchoolIdentity(schoolScheduleChildReader.findChildren(userId));
    TranslationContext translationContext = translationService.contextFor(userId);
    List<SchoolMealCalendarResponse.SchoolMealGroup> groups = new ArrayList<>();
    for (Map.Entry<SchoolIdentity, List<SchoolScheduleChild>> entry : childrenBySchool.entrySet()) {
      SchoolIdentity identity = entry.getKey();
      List<SchoolScheduleChild> schoolChildren = entry.getValue();
      List<NeisSchoolMealItem> meals =
          neisSchoolMealClient.search(
              identity.officeCode(), identity.schoolCode(), fromDate, toDate);
      groups.add(toGroup(identity, schoolChildren, meals, translationContext));
    }

    return new SchoolMealCalendarResponse(groups);
  }

  private void validateRange(LocalDate fromDate, LocalDate toDate) {
    if (toDate.isBefore(fromDate)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "종료일은 시작일보다 빠를 수 없습니다.");
    }
    long requestedDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    if (requestedDays > MAX_SCHOOL_MEAL_RANGE_DAYS) {
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

  private SchoolMealCalendarResponse.SchoolMealGroup toGroup(
      SchoolIdentity identity,
      List<SchoolScheduleChild> children,
      List<NeisSchoolMealItem> meals,
      TranslationContext translationContext) {
    List<Long> childIds = children.stream().map(SchoolScheduleChild::childId).toList();
    List<SchoolMealCalendarResponse.ChildItem> childItems =
        children.stream()
            .map(
                child ->
                    new SchoolMealCalendarResponse.ChildItem(
                        child.childId(), child.childName(), child.grade(), child.colorCode()))
            .toList();
    List<SchoolMealCalendarResponse.MealItem> mealItems =
        meals.stream().map(item -> toMealItem(item, translationContext)).toList();

    return new SchoolMealCalendarResponse.SchoolMealGroup(
        identity.groupKey(),
        identity.officeCode(),
        identity.schoolCode(),
        children.get(0).schoolName(),
        childIds,
        childItems,
        mealItems);
  }

  private SchoolMealCalendarResponse.MealItem toMealItem(
      NeisSchoolMealItem item, TranslationContext translationContext) {
    return new SchoolMealCalendarResponse.MealItem(
        item.date().format(DateTimeFormatter.ISO_LOCAL_DATE),
        item.mealCode(),
        translationService.translateMealName(translationContext, item.mealCode(), item.mealName()),
        item.mealPeopleCount(),
        translationService.translate(translationContext, item.dishName()),
        translationService.translate(translationContext, item.originInfo()),
        item.calorieInfo(),
        translationService.translateNutritionInfo(translationContext, item.nutritionInfo()));
  }

  private record SchoolIdentity(String officeCode, String schoolCode) {
    String groupKey() {
      return officeCode + ":" + schoolCode;
    }
  }
}
