package com.gachi.be.domain.schoolguide.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SchoolGuidePopularResponse {

    private List<PopularItem> items;

    public static SchoolGuidePopularResponse of(List<SchoolGuide> faqs) {
        List<PopularItem> items = faqs.stream().map(PopularItem::of).toList();
        return SchoolGuidePopularResponse.builder().items(items).build();
    }

    @Getter
    @Builder
    public static class PopularItem {
        private Long faqId;
        private String question;

        public static PopularItem of(SchoolGuide faq) {
            return PopularItem.builder()
                .faqId(faq.getId())
                .question(faq.getQuestion())
                .build();
        }
    }
}
