package com.gachi.be.domain.calendar.dto.response;

import java.util.List;

public record ElementaryTimetableCalendarResponse(List<TimetableGroup> schoolTimetables) {
  public ElementaryTimetableCalendarResponse {
    schoolTimetables = schoolTimetables == null ? List.of() : List.copyOf(schoolTimetables);
  }

  public record TimetableGroup(
      String schoolGroupKey,
      String officeCode,
      String schoolCode,
      String schoolName,
      List<Long> childIds,
      List<ChildItem> children,
      List<TimetableItem> timetables) {
    public TimetableGroup {
      childIds = childIds == null ? List.of() : List.copyOf(childIds);
      children = children == null ? List.of() : List.copyOf(children);
      timetables = timetables == null ? List.of() : List.copyOf(timetables);
    }
  }

  public record ChildItem(
      Long childId, String childName, Integer grade, String className, String colorCode) {}

  public record TimetableItem(
      String date,
      String academicYear,
      String semester,
      Integer grade,
      String className,
      Integer period,
      String content) {}
}
