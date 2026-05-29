package com.gachi.be.domain.school.dto.response;

import java.time.LocalDate;

public record NeisSchoolScheduleItem(
    String academicYear,
    LocalDate date,
    String eventName,
    String eventContent,
    GradeEventYn gradeEventYn) {
  public record GradeEventYn(
      String grade1, String grade2, String grade3, String grade4, String grade5, String grade6) {}
}
