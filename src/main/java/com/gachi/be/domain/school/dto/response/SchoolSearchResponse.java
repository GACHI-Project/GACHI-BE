package com.gachi.be.domain.school.dto.response;

import java.util.List;

public record SchoolSearchResponse(String keyword, int totalCount, List<SchoolSearchItem> schools) {
  public SchoolSearchResponse {
    schools = schools == null ? List.of() : List.copyOf(schools);
  }
}
