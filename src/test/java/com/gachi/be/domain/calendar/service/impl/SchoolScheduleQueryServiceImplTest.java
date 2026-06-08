package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.calendar.service.impl.SchoolScheduleChildReader.SchoolScheduleChild;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchoolScheduleQueryServiceImplTest {

  private final SchoolScheduleChildReader schoolScheduleChildReader =
      mock(SchoolScheduleChildReader.class);
  private final NeisSchoolScheduleClient neisSchoolScheduleClient =
      mock(NeisSchoolScheduleClient.class);
  private final SchoolScheduleQueryServiceImpl service =
      new SchoolScheduleQueryServiceImpl(schoolScheduleChildReader, neisSchoolScheduleClient);

  @Test
  void getSchoolSchedulesGroupsChildrenByOfficeCodeAndSchoolCode() {
    SchoolScheduleChild first = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    SchoolScheduleChild second = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 2, "#FFCC00");
    SchoolScheduleChild sameNameOtherSchool =
        child(3L, "셋째", "화랑초등학교", "7611076", "J10", 1, "#3366FF");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(schoolScheduleChildReader.findChildren(10L))
        .thenReturn(List.of(first, second, sameNameOtherSchool));
    when(neisSchoolScheduleClient.search(eq("B10"), eq("7051173"), eq(fromDate), eq(toDate)))
        .thenReturn(List.of(schedule("시업식", LocalDate.of(2026, 3, 2))));
    when(neisSchoolScheduleClient.search(eq("J10"), eq("7611076"), eq(fromDate), eq(toDate)))
        .thenReturn(List.of(schedule("재량휴업일", LocalDate.of(2026, 3, 5))));

    var response = service.getSchoolSchedules(10L, fromDate, toDate);

    assertThat(response.schoolSchedules()).hasSize(2);
    assertThat(response.schoolSchedules().get(0).schoolGroupKey()).isEqualTo("B10:7051173");
    assertThat(response.schoolSchedules().get(0).childIds()).containsExactly(1L, 2L);
    assertThat(response.schoolSchedules().get(0).schedules().get(0).eventName()).isEqualTo("시업식");
    assertThat(response.schoolSchedules().get(1).schoolGroupKey()).isEqualTo("J10:7611076");
    assertThat(response.schoolSchedules().get(1).childIds()).containsExactly(3L);
    verify(neisSchoolScheduleClient, times(2)).search(any(), any(), eq(fromDate), eq(toDate));
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
    return new NeisSchoolScheduleItem(
        "2026",
        date,
        eventName,
        null,
        new NeisSchoolScheduleItem.GradeEventYn("Y", "Y", "Y", "Y", "Y", "Y"));
  }
}
