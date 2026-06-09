package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.service.impl.NeisCalendarTranslationService.TranslationContext;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisElementaryTimetableClient;
import com.gachi.be.domain.school.dto.response.NeisElementaryTimetableItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElementaryTimetableQueryServiceImplTest {

  private final SchoolScheduleChildReader schoolScheduleChildReader =
      mock(SchoolScheduleChildReader.class);
  private final NeisElementaryTimetableClient neisElementaryTimetableClient =
      mock(NeisElementaryTimetableClient.class);
  private final NeisCalendarTranslationService translationService =
      mock(NeisCalendarTranslationService.class);
  private final TranslationContext translationContext =
      new TranslationContext("KO", new HashMap<>());
  private final ElementaryTimetableQueryServiceImpl service =
      new ElementaryTimetableQueryServiceImpl(
          schoolScheduleChildReader, neisElementaryTimetableClient, translationService);

  @BeforeEach
  void setUpTranslation() {
    when(translationService.contextFor(10L)).thenReturn(translationContext);
    when(translationService.translate(eq(translationContext), nullable(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(1));
  }

  @Test
  void getElementaryTimetablesQueriesDistinctGradesAndClassesPerSchool() {
    SchoolScheduleChild first = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "1", "#22CC88");
    SchoolScheduleChild sameClass = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 4, "1", "#FFCC00");
    SchoolScheduleChild otherClass = child(3L, "셋째", "화랑초등학교", "7051173", "B10", 4, "2", "#3366FF");
    SchoolScheduleChild otherGrade = child(4L, "넷째", "화랑초등학교", "7051173", "B10", 2, "1", "#6655EE");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(schoolScheduleChildReader.findChildren(10L))
        .thenReturn(List.of(first, sameClass, otherClass, otherGrade));
    when(neisElementaryTimetableClient.search(
            eq("B10"), eq("7051173"), eq(fromDate), eq(toDate), eq(4), eq("1")))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 4, "1", 2, "수학")));
    when(neisElementaryTimetableClient.search(
            eq("B10"), eq("7051173"), eq(fromDate), eq(toDate), eq(4), eq("2")))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 4, "2", 3, "과학")));
    when(neisElementaryTimetableClient.search(
            eq("B10"), eq("7051173"), eq(fromDate), eq(toDate), eq(2), eq("1")))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 2, "1", 1, "국어")));

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    assertThat(response.schoolTimetables().get(0).childIds()).containsExactly(1L, 2L, 3L, 4L);
    assertThat(response.schoolTimetables().get(0).children())
        .extracting("childId", "grade", "className")
        .containsExactly(
            tuple(1L, 4, "1"), tuple(2L, 4, "1"), tuple(3L, 4, "2"), tuple(4L, 2, "1"));
    assertThat(response.schoolTimetables().get(0).timetables())
        .extracting("grade", "className", "period", "content")
        .containsExactly(tuple(2, "1", 1, "국어"), tuple(4, "1", 2, "수학"), tuple(4, "2", 3, "과학"));
    verify(neisElementaryTimetableClient, times(1))
        .search("B10", "7051173", fromDate, toDate, 4, "1");
    verify(neisElementaryTimetableClient, times(1))
        .search("B10", "7051173", fromDate, toDate, 4, "2");
    verify(neisElementaryTimetableClient, times(1))
        .search("B10", "7051173", fromDate, toDate, 2, "1");
  }

  @Test
  void getElementaryTimetablesTranslatesContentByUserLanguage() {
    TranslationContext englishContext = new TranslationContext("US", new HashMap<>());
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "1", "#22CC88");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(translationService.contextFor(20L)).thenReturn(englishContext);
    when(translationService.translate(englishContext, "수학")).thenReturn("Math");
    when(schoolScheduleChildReader.findChildren(20L)).thenReturn(List.of(child));
    when(neisElementaryTimetableClient.search("B10", "7051173", fromDate, toDate, 4, "1"))
        .thenReturn(List.of(timetable(LocalDate.of(2026, 3, 2), 4, "1", 2, "수학")));

    var response = service.getElementaryTimetables(20L, fromDate, toDate);

    assertThat(response.schoolTimetables().get(0).timetables().get(0).content()).isEqualTo("Math");
  }

  @Test
  void getElementaryTimetablesThrowsWhenChildSchoolIdentityIsMissing() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", null, "B10", 4, "1", "#22CC88");
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
  void getElementaryTimetablesSkipsNeisCallWhenChildClassNameIsMissing() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, null, "#22CC88");
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));

    var response =
        service.getElementaryTimetables(10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

    assertThat(response.schoolTimetables()).hasSize(1);
    assertThat(response.schoolTimetables().get(0).children())
        .extracting("childId", "grade", "className")
        .containsExactly(tuple(1L, 4, null));
    assertThat(response.schoolTimetables().get(0).timetables()).isEmpty();
    verifyNoInteractions(neisElementaryTimetableClient);
  }

  @Test
  void getElementaryTimetablesAllowsExactlyOneYearRange() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "1", "#22CC88");
    LocalDate fromDate = LocalDate.of(2026, 1, 1);
    LocalDate toDate = LocalDate.of(2027, 1, 1);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisElementaryTimetableClient.search("B10", "7051173", fromDate, toDate, 4, "1"))
        .thenReturn(List.of());

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    verify(neisElementaryTimetableClient, times(1))
        .search("B10", "7051173", fromDate, toDate, 4, "1");
  }

  @Test
  void getElementaryTimetablesAllowsLeapYearRangeWithinLimit() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "1", "#22CC88");
    LocalDate fromDate = LocalDate.of(2024, 1, 1);
    LocalDate toDate = LocalDate.of(2024, 12, 31);
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));
    when(neisElementaryTimetableClient.search("B10", "7051173", fromDate, toDate, 4, "1"))
        .thenReturn(List.of());

    var response = service.getElementaryTimetables(10L, fromDate, toDate);

    assertThat(response.schoolTimetables()).hasSize(1);
    verify(neisElementaryTimetableClient, times(1))
        .search("B10", "7051173", fromDate, toDate, 4, "1");
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
      String className,
      String colorCode) {
    return new SchoolScheduleChild(
        id, name, schoolName, schoolCode, officeCode, grade, className, colorCode);
  }

  private NeisElementaryTimetableItem timetable(
      LocalDate date, Integer grade, String className, Integer period, String content) {
    return new NeisElementaryTimetableItem("2026", "1", date, grade, className, period, content);
  }

  private org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.api.Assertions.tuple(values);
  }
}
