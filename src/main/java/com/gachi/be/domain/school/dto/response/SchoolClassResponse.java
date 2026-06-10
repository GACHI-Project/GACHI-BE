package com.gachi.be.domain.school.dto.response;

import java.util.List;

public record SchoolClassResponse(
    String officeCode,
    String schoolCode,
    String academicYear,
    Integer grade,
    int totalCount,
    List<SchoolClassItem> classes) {
  public SchoolClassResponse {
    classes = classes == null ? List.of() : List.copyOf(classes);
  }
}
