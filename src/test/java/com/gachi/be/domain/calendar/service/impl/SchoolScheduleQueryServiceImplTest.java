package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.service.impl.NeisCalendarTranslationService.TranslationContext;
import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchoolScheduleQueryServiceImplTest {

  private final SchoolScheduleChildReader schoolScheduleChildReader =
      mock(SchoolScheduleChildReader.class);
  private final NeisSchoolScheduleClient neisSchoolScheduleClient =
      mock(NeisSchoolScheduleClient.class);
  private final NeisCalendarTranslationService translationService =
      mock(NeisCalendarTranslationService.class);
  private final TranslationContext translationContext =
      new TranslationContext("KO", new HashMap<>());
  private final SchoolScheduleQueryServiceImpl service =
      new SchoolScheduleQueryServiceImpl(
          schoolScheduleChildReader, neisSchoolScheduleClient, translationService);

  @BeforeEach
  void setUpTranslation() {
    when(translationService.contextFor(10L)).thenReturn(translationContext);
    when(translationService.translateScheduleText(eq(translationContext), nullable(String.class)))
        .thenAnswer(invocation -> invocation.getArgument(1));
  }

  @Test
  void getSchoolSchedulesSeparatesCommonHolidaysAndFiltersByChildGrades() {
    SchoolScheduleChild fourthGrade = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    SchoolScheduleChild secondGrade = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 2, "#FFCC00");
    SchoolScheduleChild otherSchool = child(3L, "셋째", "가치초등학교", "7611076", "J10", 1, "#3366FF");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 5, 31);
    when(schoolScheduleChildReader.findChildren(10L))
        .thenReturn(List.of(fourthGrade, secondGrade, otherSchool));
    when(neisSchoolScheduleClient.search(eq("B10"), eq("7051173"), eq(fromDate), eq(toDate)))
        .thenReturn(
            List.of(
                schedule("대체공휴일", LocalDate.of(2026, 3, 2)),
                schedule("토요휴업일", LocalDate.of(2026, 3, 7)),
                schedule("4학년 현장체험학습", LocalDate.of(2026, 3, 10), "N", "N", "N", "Y", "N", "N"),
                schedule("6학년 졸업앨범 촬영", LocalDate.of(2026, 3, 11), "N", "N", "N", "N", "N", "Y"),
                schedule("시업식", LocalDate.of(2026, 3, 3))));
    when(neisSchoolScheduleClient.search(eq("J10"), eq("7611076"), eq(fromDate), eq(toDate)))
        .thenReturn(
            List.of(
                schedule("대체 공휴일", LocalDate.of(2026, 3, 2)),
                schedule("어린이날", LocalDate.of(2026, 5, 5)),
                schedule("토요공휴일", LocalDate.of(2026, 3, 7)),
                schedule("재량휴업일", LocalDate.of(2026, 3, 5))));

    var response = service.getSchoolSchedules(10L, fromDate, toDate);

    assertThat(response.commonHolidays())
        .extracting("date", "eventName")
        .containsExactly(tuple("2026-03-02", "대체공휴일"), tuple("2026-05-05", "어린이날"));
    assertThat(response.schoolSchedules()).hasSize(2);
    assertThat(response.schoolSchedules().get(0).schoolGroupKey()).isEqualTo("B10:7051173");
    assertThat(response.schoolSchedules().get(0).childIds()).containsExactly(1L, 2L);
    assertThat(response.schoolSchedules().get(0).schedules())
        .extracting("eventName")
        .containsExactly("4학년 현장체험학습", "시업식");
    assertThat(response.schoolSchedules().get(1).schoolGroupKey()).isEqualTo("J10:7611076");
    assertThat(response.schoolSchedules().get(1).childIds()).containsExactly(3L);
    assertThat(response.schoolSchedules().get(1).schedules())
        .extracting("eventName")
        .containsExactly("재량휴업일");
    verify(neisSchoolScheduleClient, times(2)).search(any(), any(), eq(fromDate), eq(toDate));
  }

  @Test
  void getSchoolSchedulesTranslatesCommonHolidaysAndSchoolSchedulesByUserLanguage() {
    TranslationContext englishContext = new TranslationContext("US", new HashMap<>());
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 5, 31);
    when(translationService.contextFor(20L)).thenReturn(englishContext);
    when(translationService.translateScheduleText(englishContext, "대체공휴일"))
        .thenReturn("Substitute holiday");
    when(translationService.translateScheduleText(englishContext, "시업식"))
        .thenReturn("Opening ceremony");
    when(schoolScheduleChildReader.findChildren(20L)).thenReturn(List.of(child));
    when(neisSchoolScheduleClient.search("B10", "7051173", fromDate, toDate))
        .thenReturn(
            List.of(
                schedule("대체공휴일", LocalDate.of(2026, 3, 2)),
                schedule("시업식", LocalDate.of(2026, 3, 3))));

    var response = service.getSchoolSchedules(20L, fromDate, toDate);

    assertThat(response.commonHolidays().get(0).eventName()).isEqualTo("Substitute holiday");
    assertThat(response.schoolSchedules().get(0).schedules().get(0).eventName())
        .isEqualTo("Opening ceremony");
  }

  @Test
  void getSchoolSchedulesThrowsWhenChildSchoolIdentityIsMissing() {
    SchoolScheduleChild child = child(1L, "첫째", "화랑초등학교", null, "B10", 4, "#22CC88");
    when(schoolScheduleChildReader.findChildren(10L)).thenReturn(List.of(child));

    assertThatThrownBy(
            () ->
                service.getSchoolSchedules(
                    10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  @Test
  void getSchoolSchedulesThrowsWhenDateRangeExceedsOneYear() {
    assertThatThrownBy(
            () ->
                service.getSchoolSchedules(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 3)))
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
    return new SchoolScheduleChild(
        id, name, schoolName, schoolCode, officeCode, grade, "1", colorCode);
  }

  private NeisSchoolScheduleItem schedule(String eventName, LocalDate date) {
    return schedule(eventName, date, "Y", "Y", "Y", "Y", "Y", "Y");
  }

  private NeisSchoolScheduleItem schedule(
      String eventName,
      LocalDate date,
      String grade1,
      String grade2,
      String grade3,
      String grade4,
      String grade5,
      String grade6) {
    return new NeisSchoolScheduleItem(
        "2026",
        date,
        eventName,
        null,
        new NeisSchoolScheduleItem.GradeEventYn(grade1, grade2, grade3, grade4, grade5, grade6));
  }

  private org.assertj.core.groups.Tuple tuple(Object... values) {
    return org.assertj.core.api.Assertions.tuple(values);
  }
}
