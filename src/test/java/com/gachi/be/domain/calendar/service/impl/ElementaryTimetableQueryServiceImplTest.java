package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisElementaryTimetableClient;
import com.gachi.be.domain.school.dto.response.NeisElementaryTimetableItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElementaryTimetableQueryServiceImplTest {

  private final SchoolScheduleChildReader schoolScheduleChildReader =
      mock(SchoolScheduleChildReader.class);
  private final NeisElementaryTimetableClient neisElementaryTimetableClient =
      mock(NeisElementaryTimetableClient.class);
  private final ElementaryTimetableQueryServiceImpl service =
      new ElementaryTimetableQueryServiceImpl(
          schoolScheduleChildReader, neisElementaryTimetableClient);

  @Test
  void getElementaryTimetablesQueriesDistinctGradesPerSchool() {
    SchoolScheduleChild first = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    SchoolScheduleChild sameGrade = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 4, "#FFCC00");
    SchoolScheduleChild otherGrade = child(3L, "셋째", "화랑초등학교", "7051173", "B10", 2, "#3366FF");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(schoolScheduleChildReader.findChildren(10L))
        .thenReturn(List.of(first, sameGrade, otherGrade));
    when(neisElementaryTimetableClient.search(
            eq("B10"), eq("7051173"), eq(fromDate), eq(toDate), eq(4)))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 4, 2, "수학")));
    when(neisElementaryTimetableClient.search(
            eq("B10"), eq("7051173"), eq(fromDate), eq(toDate), eq(2)))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 2, 1, "국어")));

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    assertThat(response.schoolTimetables().get(0).childIds()).containsExactly(1L, 2L, 3L);
    assertThat(response.schoolTimetables().get(0).timetables())
        .extracting("grade", "period", "content")
        .containsExactly(tuple(2, 1, "국어"), tuple(4, 2, "수학"));
    verify(neisElementaryTimetableClient, times(1)).search("B10", "7051173", fromDate, toDate, 4);
    verify(neisElementaryTimetableClient, times(1)).search("B10", "7051173", fromDate, toDate, 2);
  }

  @Test
  void getElementaryTimetablesThrowsWhenChildSchoolIdentityIsMissing() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", null, "B10", 4, "#22CC88");
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));

    assertThatThrownBy(
            () ->
                service.getElementaryTimetables(
                    10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  @Test
  void getElementaryTimetablesAllowsExactlyOneYearRange() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    LocalDate fromDate = LocalDate.of(2026, 1, 1);
    LocalDate toDate = LocalDate.of(2027, 1, 1);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisElementaryTimetableClient.search("B10", "7051173", fromDate, toDate, 4))
        .thenReturn(List.of());

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    verify(neisElementaryTimetableClient, times(1)).search("B10", "7051173", fromDate, toDate, 4);
  }

  @Test
  void getElementaryTimetablesAllowsLeapYearRangeWithinLimit() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    LocalDate fromDate = LocalDate.of(2024, 1, 1);
    LocalDate toDate = LocalDate.of(2024, 12, 31);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisElementaryTimetableClient.search("B10", "7051173", fromDate, toDate, 4))
        .thenReturn(List.of());

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    verify(neisElementaryTimetableClient, times(1)).search("B10", "7051173", fromDate, toDate, 4);
  }

  @Test
  void getElementaryTimetablesThrowsWhenDateRangeExceedsOneYear() {
    assertThatThrownBy(
            () ->
                service.getElementaryTimetables(
                    10L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  @Test
  void getElementaryTimetablesThrowsWhenLeapYearRangeExceedsLimit() {
    assertThatThrownBy(
            () ->
                service.getElementaryTimetables(
                    10L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)))
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

  private NeisElementaryTimetableItem timetable(
      LocalDate date, Integer grade, Integer period, String content) {
    return new NeisElementaryTimetableItem("2026", "1", date, grade, "1", period, content);
  }

  private org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.api.Assertions.tuple(values);
  }
}
