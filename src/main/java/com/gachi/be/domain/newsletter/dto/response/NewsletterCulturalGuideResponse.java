package com.gachi.be.domain.newsletter.dto.response;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.global.util.I18nTextResolver;
import java.util.List;

/**
 * 문화 맥락 안내 조회 응답.
 * 관련 FAQ가 없거나 캘린더 미등록 문서면 guides는 빈 배열([])
 */
public record NewsletterCulturalGuideResponse(List<GuideItem> guides) {

    public record GuideItem(
        Long faqId, SchoolGuideCategory category, String question, String answer) {}

    /**
     * @param orderedFaqs display_order 순서로 정렬된 SchoolGuide 목록
     * @param language 사용자 언어 (KO/US/ZH/VI)
     */
    public static NewsletterCulturalGuideResponse of(List<SchoolGuide> orderedFaqs, String language) {
        List<GuideItem> items =
            orderedFaqs.stream()
                .map(
                    faq ->
                        new GuideItem(
                            faq.getId(),
                            faq.getCategory(),
                            I18nTextResolver.resolve(
                                faq.getQuestionI18n(), language, faq.getQuestion()),
                            I18nTextResolver.resolve(faq.getAnswerI18n(), language, faq.getAnswer())))
                .toList();
        return new NewsletterCulturalGuideResponse(items);
    }
}
