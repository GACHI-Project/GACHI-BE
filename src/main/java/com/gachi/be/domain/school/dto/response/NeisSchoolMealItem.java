package com.gachi.be.domain.school.dto.response;

import java.time.LocalDate;

public record NeisSchoolMealItem(
    LocalDate date,
    String mealCode,
    String mealName,
    Integer mealPeopleCount,
    String dishName,
    String originInfo,
    String calorieInfo,
    String nutritionInfo) {}
