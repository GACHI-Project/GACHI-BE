package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 홈화면 최근 가정통신문 조회 API 응답 DTO. */
public record NewsletterRecentResponse(List<DateGroup> groups) {

  public record DateGroup(String date, List<RecentItem> items) {}

  /** 홈화면 가정통신문 카드 하나에 해당하는 데이터. */
  public record RecentItem(Long newsletterId, String title, String childName, Integer childGrade) {

    public static RecentItem from(Newsletter newsletter) {
      return new RecentItem(
          newsletter.getId(),
          newsletter.getTitle(),
          newsletter.getChildName(),
          newsletter.getChildGrade());
    }
  }

  public static final ZoneId KST = ZoneId.of("Asia/Seoul");
  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
}
