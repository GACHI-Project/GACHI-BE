package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.global.util.I18nTextResolver;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuidePopularResponse {

  private List<PopularItem> items;

  public static SchoolGuidePopularResponse of(List<SchoolGuide> faqs, String language) {
    List<PopularItem> items = faqs.stream().map(faq -> PopularItem.of(faq, language)).toList();
    return SchoolGuidePopularResponse.builder().items(items).build();
  }

  @Getter
  @Builder
  public static class PopularItem {
    private Long faqId;
    private String question;

    public static PopularItem of(SchoolGuide faq, String language) {
      return PopularItem.builder()
          .faqId(faq.getId())
          .question(I18nTextResolver.resolve(faq.getQuestionI18n(), language, faq.getQuestion()))
          .build();
    }
  }
}
