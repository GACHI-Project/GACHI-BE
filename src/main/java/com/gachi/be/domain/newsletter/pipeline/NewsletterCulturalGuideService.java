package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.NewsletterCulturalGuide;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.CulturalGuideFaqCandidate;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.CulturalGuideResponse;
import com.gachi.be.domain.newsletter.pipeline.AiNewsletterClient.SelectedCulturalGuide;
import com.gachi.be.domain.newsletter.repository.NewsletterCulturalGuideRepository;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.repository.SchoolGuideRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문화 맥락 안내(학교 생활 가이드 FAQ) 선정 및 저장.
 *
 * 파이프라인 STEP 8에서 호출. NewsletterDateCandidateService와 동일하게 REQUIRES_NEW로 자체 트랜잭션을 관리.
 * (@Async + @Transactional 충돌 방지 원칙)
 *
 * AI는 faqId만 선정하고, 질문/답변 본문은 school_guide DB 원문을 그대로 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterCulturalGuideService {

    /** 화면 노출 최대 개수. 관련 FAQ가 없으면 0개(빈 배열)도 정상이다. */
    private static final int MAX_GUIDE_COUNT = 2;

    private final NewsletterRepository newsletterRepository;
    private final SchoolGuideRepository schoolGuideRepository;
    private final NewsletterCulturalGuideRepository newsletterCulturalGuideRepository;
    private final AiNewsletterClient aiNewsletterClient;

    /** 가정통신문과 관련된 FAQ를 AI로 선정해서 매핑을 교체 저장한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void extractAndReplace(Long newsletterId, String originalText) {

        if (originalText == null || originalText.isBlank()) {
            log.debug("[CulturalGuide] 원문이 비어 있어 선정을 건너뜁니다. newsletterId={}", newsletterId);
            return;
        }

        Newsletter newsletter = newsletterRepository.findById(newsletterId).orElse(null);
        if (newsletter == null) {
            log.warn("[CulturalGuide] newsletter를 찾을 수 없습니다. newsletterId={}", newsletterId);
            return;
        }

        List<SchoolGuide> allFaqs = schoolGuideRepository.findAllByOrderByCreatedAtAsc();
        if (allFaqs.isEmpty()) {
            log.warn("[CulturalGuide] school_guide 데이터가 없어 선정을 건너뜁니다. newsletterId={}", newsletterId);
            return;
        }

        // 후보는 항상 한국어 question을 사용한다. (AI 프롬프트가 한국어 기준으로 작성되어 있음)
        List<CulturalGuideFaqCandidate> candidates = new ArrayList<>();
        for (SchoolGuide faq : allFaqs) {
            if (faq.getQuestion() == null || faq.getQuestion().isBlank()) {
                continue;
            }
            candidates.add(
                new CulturalGuideFaqCandidate(
                    faq.getId(), faq.getCategory().name(), faq.getQuestion().trim()));
        }

        CulturalGuideResponse response =
            aiNewsletterClient.selectCulturalGuides(
                originalText, newsletter.getTitle(), newsletter.getSummary(), candidates);

        // 재분석 대비: 기존 매핑을 먼저 비운다 (유니크 인덱스 충돌 방지)
        newsletterCulturalGuideRepository.deleteByNewsletterId(newsletterId);
        newsletterCulturalGuideRepository.flush();

        List<NewsletterCulturalGuide> entities =
            toEntities(newsletterId, newsletter.getUserId(), candidates, response.selectedFaqs());

        if (entities.isEmpty()) {
            log.info("[CulturalGuide] 관련 FAQ가 선정되지 않았습니다. newsletterId={}", newsletterId);
            return;
        }

        newsletterCulturalGuideRepository.saveAll(entities);
        log.info(
            "[CulturalGuide] 문화 맥락 안내 {}개 저장 완료. newsletterId={}, faqIds={}",
            entities.size(),
            newsletterId,
            entities.stream().map(NewsletterCulturalGuide::getSchoolGuideId).toList());
    }

    /** AI 응답을 엔티티로 변환. 후보에 없는 id, 중복 id, 최대 개수 초과를 방어. */
    private List<NewsletterCulturalGuide> toEntities(
        Long newsletterId,
        Long userId,
        List<CulturalGuideFaqCandidate> candidates,
        List<SelectedCulturalGuide> selectedFaqs) {

        if (selectedFaqs == null || selectedFaqs.isEmpty()) {
            return List.of();
        }

        // O(1) contains 조회를 위해 HashSet 사용 (N+1 / 반복 탐색 방지 원칙)
        Set<Long> allowedFaqIds = new HashSet<>();
        for (CulturalGuideFaqCandidate candidate : candidates) {
            allowedFaqIds.add(candidate.faqId());
        }

        Set<Long> seen = new HashSet<>();
        List<NewsletterCulturalGuide> entities = new ArrayList<>();

        for (SelectedCulturalGuide selected : selectedFaqs) {
            Long faqId = selected.faqId();

            if (!allowedFaqIds.contains(faqId)) {
                log.warn("[CulturalGuide] 후보에 없는 faqId가 반환되어 제외합니다. faqId={}", faqId);
                continue;
            }
            if (!seen.add(faqId)) {
                continue; // 중복 제외
            }

            entities.add(
                NewsletterCulturalGuide.builder()
                    .newsletterId(newsletterId)
                    .userId(userId)
                    .schoolGuideId(faqId)
                    .displayOrder(entities.size())
                    .build());

            if (entities.size() >= MAX_GUIDE_COUNT) {
                break;
            }
        }

        return entities;
    }
}
