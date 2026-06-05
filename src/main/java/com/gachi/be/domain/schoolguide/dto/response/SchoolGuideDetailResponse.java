package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuideDetailResponse {

    private Long faqId;
    private SchoolGuideCategory category;
    private String question;
    private String answer;

    public static SchoolGuideDetailResponse of(SchoolGuide faq) {
        return SchoolGuideDetailResponse.builder()
            .faqId(faq.getId())
            .category(faq.getCategory())
            .question(faq.getQuestion())
            .answer(faq.getAnswer())
            .build();
    }
}
