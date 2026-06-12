package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.global.util.I18nTextResolver;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuideDetailResponse {

  private Long faqId;
  private SchoolGuideCategory category;
  private String question;
  private String answer;

  public static SchoolGuideDetailResponse of(SchoolGuide faq, String language) {
      return SchoolGuideDetailResponse.builder()
          .faqId(faq.getId())
          .category(faq.getCategory())
          .question(I18nTextResolver.resolve(faq.getQuestionI18n(), language, faq.getQuestion()))
          .answer(I18nTextResolver.resolve(faq.getAnswerI18n(), language, faq.getAnswer()))
          .build();
  }
}
