package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.global.util.I18nTextResolver;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuideListResponse {

  private int totalCount;
  private List<SchoolGuideItem> items;

  public static SchoolGuideListResponse of(List<SchoolGuide> faqs, String language) {
    List<SchoolGuideItem> items =
        faqs.stream().map(faq -> SchoolGuideItem.of(faq, language)).toList();
    return SchoolGuideListResponse.builder().totalCount(items.size()).items(items).build();
  }

  @Getter
  @Builder
  public static class SchoolGuideItem {
    private Long faqId;
    private SchoolGuideCategory category;
    private String question;

    public static SchoolGuideItem of(SchoolGuide faq, String language) {
      return SchoolGuideItem.builder()
          .faqId(faq.getId())
          .category(faq.getCategory())
          .question(I18nTextResolver.resolve(faq.getQuestionI18n(), language, faq.getQuestion()))
          .build();
    }
  }
}
