package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

public record SchoolMealCalendarResponse(List<SchoolMealGroup> schoolMeals) {
  public SchoolMealCalendarResponse {
    schoolMeals = schoolMeals == null ? List.of() : List.copyOf(schoolMeals);
  }

  public record SchoolMealGroup(
      String schoolGroupKey,
      String officeCode,
      String schoolCode,
      String schoolName,
      List<Long> childIds,
      List<ChildItem> children,
      List<MealItem> meals) {
    public SchoolMealGroup {
      childIds = childIds == null ? List.of() : List.copyOf(childIds);
      children = children == null ? List.of() : List.copyOf(children);
      meals = meals == null ? List.of() : List.copyOf(meals);
    }
  }

  public record ChildItem(Long childId, String childName, Integer grade, String colorCode) {}

  public record MealItem(
      String date,
      String mealCode,
      String mealName,
      Integer mealPeopleCount,
      String dishName,
      String originInfo,
      String calorieInfo,
      String nutritionInfo) {}
}
