package com.gachi.be.domain.calendar.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.child.entity.Child;
import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.school.client.NeisSchoolScheduleClient;
import com.gachi.be.domain.school.dto.response.NeisSchoolScheduleItem;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SchoolScheduleQueryServiceImplTest {

  private final ChildRepository childRepository = mock(ChildRepository.class);
  private final NeisSchoolScheduleClient neisSchoolScheduleClient =
      mock(NeisSchoolScheduleClient.class);
  private final SchoolScheduleQueryServiceImpl service =
      new SchoolScheduleQueryServiceImpl(childRepository, neisSchoolScheduleClient);

  @Test
  void getSchoolSchedulesGroupsChildrenByOfficeCodeAndSchoolCode() {
    Child first = child(1L, "첫째", "화랑초등학교", "7051173", "B10", 4, "#22CC88");
    Child second = child(2L, "둘째", "화랑초등학교", "7051173", "B10", 2, "#FFCC00");
    Child sameNameOtherSchool = child(3L, "셋째", "화랑초등학교", "7611076", "J10", 1, "#3366FF");
    LocalDate fromDate = LocalDate.of(2026, 3, 1);
    LocalDate toDate = LocalDate.of(2026, 3, 31);
    when(childRepository.findByUserIdAndDeletedAtIsNull(10L))
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
    Child child = child(1L, "첫째", "화랑초등학교", null, "B10", 4, "#22CC88");
    when(childRepository.findByUserIdAndDeletedAtIsNull(10L)).thenReturn(List.of(child));

    assertThatThrownBy(
            () ->
                service.getSchoolSchedules(
                    10L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
  }

  private Child child(
      Long id,
      String name,
      String schoolName,
      String schoolCode,
      String officeCode,
      int grade,
      String colorCode) {
    Child child =
        Child.builder()
            .name(name)
            .schoolName(schoolName)
            .schoolCode(schoolCode)
            .officeCode(officeCode)
            .grade(grade)
            .colorCode(colorCode)
            .build();
    ReflectionTestUtils.setField(child, "id", id);
    return child;
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
