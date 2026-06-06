package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolMealClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolMealItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchoolMealQueryServiceImplTest {

  private final SchoolScheduleChildReader schoolScheduleChildReader =
      mock(SchoolScheduleChildReader.class);
  private final NeisSchoolMealClient neisSchoolMealClient = mock(NeisSchoolMealClient.class);
  private final SchoolMealQueryServiceImpl service =
      new SchoolMealQueryServiceImpl(schoolScheduleChildReader, neisSchoolMealClient);

  @Test
  void getSchoolMealsGroupsChildrenByOfficeCodeAndSchoolCode() {
    SchoolScheduleChild first = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    SchoolScheduleChild second = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 2, "#FFCC00");
    SchoolScheduleChild otherSchool = child(3L, "셋째", "가치초등학교", "7611076", "J10", 1, "#3366FF");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(schoolScheduleChildReader.findChildren(10L))
        .thenReturn(List.of(first, second, otherSchool));
    when(neisSchoolMealClient.search(eq("B10"), eq("7051173"), eq(fromDate), eq(toDate)))
        .thenReturn(List.of(meal(LocalDate.of(2026, 3, 2), "중식", "현미밥")));
    when(neisSchoolMealClient.search(eq("J10"), eq("7611076"), eq(fromDate), eq(toDate)))
        .thenReturn(List.of(meal(LocalDate.of(2026, 3, 3), "중식", "보리밥")));

    var response = service.getSchoolMeals(10L, fromDate, toDate);

    assertThat(response.schoolMeals()).hasSize(2);
    assertThat(response.schoolMeals().get(0).schoolGroupKey()).isEqualTo("B10:7051173");
    assertThat(response.schoolMeals().get(0).childIds()).containsExactly(1L, 2L);
    assertThat(response.schoolMeals().get(0).meals().get(0).dishName()).isEqualTo("현미밥");
    assertThat(response.schoolMeals().get(1).schoolGroupKey()).isEqualTo("J10:7611076");
    verify(neisSchoolMealClient, times(1)).search("B10", "7051173", fromDate, toDate);
    verify(neisSchoolMealClient, times(1)).search("J10", "7611076", fromDate, toDate);
  }

  @Test
  void getSchoolMealsThrowsWhenChildSchoolIdentityIsMissing() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", null, "B10", 4, "#22CC88");
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));

    assertThatThrownBy(
            () -> service.getSchoolMeals(10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  @Test
  void getSchoolMealsAllowsExactlyOneYearRange() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    LocalDate fromDate = LocalDate.of(2026, 1, 1);
    LocalDate toDate = LocalDate.of(2027, 1, 1);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisSchoolMealClient.search("B10", "7051173", fromDate, toDate)).thenReturn(List.of());

    var response = service.getSchoolMeals(10L, fromDate, toDate);

    assertThat(response.schoolMeals()).hasSize(1);
    verify(neisSchoolMealClient, times(1)).search("B10", "7051173", fromDate, toDate);
  }

  @Test
  void getSchoolMealsAllowsLeapYearRangeWithinLimit() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    LocalDate fromDate = LocalDate.of(2024, 1, 1);
    LocalDate toDate = LocalDate.of(2024, 12, 31);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisSchoolMealClient.search("B10", "7051173", fromDate, toDate)).thenReturn(List.of());

    var response = service.getSchoolMeals(10L, fromDate, toDate);

    assertThat(response.schoolMeals()).hasSize(1);
    verify(neisSchoolMealClient, times(1)).search("B10", "7051173", fromDate, toDate);
  }

  @Test
  void getSchoolMealsThrowsWhenDateRangeExceedsOneYear() {
    assertThatThrownBy(
            () -> service.getSchoolMeals(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  @Test
  void getSchoolMealsThrowsWhenLeapYearRangeExceedsLimit() {
    assertThatThrownBy(
            () -> service.getSchoolMeals(10L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  private SchoolScheduleChild child(
      Long id,
      String name,
      String schoolName,
      String schoolCode,
      String officeCode,
      int grade,
      String colorCode) {
    return new SchoolScheduleChild(id, name, schoolName, schoolCode, officeCode, grade, colorCode);
  }

  private NeisSchoolMealItem meal(LocalDate date, String mealName, String dishName) {
    return new NeisSchoolMealItem(date, "2", mealName, 120, dishName, null, "612.3 Kcal", null);
  }
}
