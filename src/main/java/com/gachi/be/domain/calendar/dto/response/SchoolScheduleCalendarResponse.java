package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

public record SchoolScheduleCalendarResponse(List<SchoolScheduleGroup> schoolSchedules) {
  public SchoolScheduleCalendarResponse {
    schoolSchedules = schoolSchedules == null ? List.of() : List.copyOf(schoolSchedules);
  }

  public record SchoolScheduleGroup(
      String schoolGroupKey,
      String officeCode,
      String schoolCode,
      String schoolName,
      List<Long> childIds,
      List<ChildItem> children,
      List<ScheduleItem> schedules) {
    public SchoolScheduleGroup {
      childIds = childIds == null ? List.of() : List.copyOf(childIds);
      children = children == null ? List.of() : List.copyOf(children);
      schedules = schedules == null ? List.of() : List.copyOf(schedules);
    }
  }

  public record ChildItem(Long childId, String childName, Integer grade, String colorCode) {}

  public record ScheduleItem(
      String date,
      String academicYear,
      String eventName,
      String eventContent,
      GradeEventYn gradeEventYn) {}

  public record GradeEventYn(
      String grade1, String grade2, String grade3, String grade4, String grade5, String grade6) {}
}
