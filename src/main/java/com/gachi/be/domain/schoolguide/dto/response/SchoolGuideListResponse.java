package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuideListResponse {

  private int totalCount;
  private List<SchoolGuideItem> items;

  public static SchoolGuideListResponse of(List<SchoolGuide> faqs) {
    List<SchoolGuideItem> items = faqs.stream().map(SchoolGuideItem::of).toList();
    return SchoolGuideListResponse.builder().totalCount(items.size()).items(items).build();
  }

  @Getter
  @Builder
  public static class SchoolGuideItem {
    private Long faqId;
    private SchoolGuideCategory category;
    private String question;

    public static SchoolGuideItem of(SchoolGuide faq) {
      return SchoolGuideItem.builder()
          .faqId(faq.getId())
          .category(faq.getCategory())
          .question(faq.getQuestion())
          .build();
    }
  }
}
