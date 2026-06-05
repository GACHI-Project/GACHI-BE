package com.gachi.be.domain.schoolguide.service.impl;

import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideCreateRequest;
import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideUpdateRequest;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideCategoryResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideCategoryResponse.CategoryItem;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideDetailResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideListResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuidePopularResponse;
import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.domain.schoolguide.repository.SchoolGuideRepository;
import com.gachi.be.domain.schoolguide.service.SchoolGuideService;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolGuideServiceImpl implements SchoolGuideService {

  private final SchoolGuideRepository schoolGuideRepository;

  /** 카테고리별 FAQ 개수 조회. */
  @Override
  @Transactional(readOnly = true)
  public SchoolGuideCategoryResponse getCategoryCounts() {

    // DB에서 카테고리별 개수 집계
    List<Object[]> rawCounts = schoolGuideRepository.countByCategory();

    // category → count 맵 생성
    Map<SchoolGuideCategory, Long> countMap =
        rawCounts.stream()
            .collect(Collectors.toMap(row -> (SchoolGuideCategory) row[0], row -> (Long) row[1]));

    List<CategoryItem> items =
        Arrays.stream(SchoolGuideCategory.values())
            .map(cat -> CategoryItem.of(cat, countMap.getOrDefault(cat, 0L)))
            .toList();

    return SchoolGuideCategoryResponse.of(items);
  }

  /** 주간 인기 질문 TOP 2 조회. */
  @Override
  @Transactional(readOnly = true)
  public SchoolGuidePopularResponse getPopularFaqs() {
    List<SchoolGuide> faqs = schoolGuideRepository.findTop2ByOrderByWeeklyViewCountDesc();
    return SchoolGuidePopularResponse.of(faqs);
  }

  /** FAQ 목록 조회 (카테고리 필터 or 검색). */
  @Override
  @Transactional(readOnly = true)
  public SchoolGuideListResponse getFaqs(SchoolGuideCategory category, String search) {

    List<SchoolGuide> faqs;

    if (category != null) {
      faqs = schoolGuideRepository.findByCategoryOrderByCreatedAtAsc(category);
    } else if (search != null && !search.isBlank()) {
      faqs = schoolGuideRepository.findByQuestionContainingIgnoreCaseOrderByCreatedAtAsc(search);
    } else {
      faqs = schoolGuideRepository.findAll();
    }

    return SchoolGuideListResponse.of(faqs);
  }

  /** FAQ 상세 조회 + weekly_view_count 증가. */
  @Override
  @Transactional
  public SchoolGuideDetailResponse getFaqDetail(Long faqId) {

    SchoolGuide faq =
        schoolGuideRepository
            .findById(faqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_GUIDE_NOT_FOUND));

    faq.incrementWeeklyViewCount();

    log.debug("[SchoolGuide] 상세 조회. faqId={}, weeklyViewCount={}", faqId, faq.getWeeklyViewCount());

    return SchoolGuideDetailResponse.of(faq);
  }

  /** FAQ 등록. */
  @Override
  @Transactional
  public Long createFaq(SchoolGuideCreateRequest request) {

    SchoolGuide faq =
        SchoolGuide.builder()
            .category(request.getCategory())
            .question(request.getQuestion())
            .answer(request.getAnswer())
            .build();

    SchoolGuide saved = schoolGuideRepository.save(faq);
    log.info("[SchoolGuide] FAQ 등록. faqId={}, category={}", saved.getId(), saved.getCategory());
    return saved.getId();
  }

  /** FAQ 수정. */
  @Override
  @Transactional
  public void updateFaq(Long faqId, SchoolGuideUpdateRequest request) {

    SchoolGuide faq =
        schoolGuideRepository
            .findById(faqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_GUIDE_NOT_FOUND));

    if (request.getCategory() != null) faq.updateCategory(request.getCategory());
    if (request.getQuestion() != null && !request.getQuestion().isBlank())
      faq.updateQuestion(request.getQuestion());
    if (request.getAnswer() != null && !request.getAnswer().isBlank())
      faq.updateAnswer(request.getAnswer());

    log.info("[SchoolGuide] FAQ 수정. faqId={}", faqId);
  }

  /** FAQ 삭제. */
  @Override
  @Transactional
  public void deleteFaq(Long faqId) {

    SchoolGuide faq =
        schoolGuideRepository
            .findById(faqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_GUIDE_NOT_FOUND));

    schoolGuideRepository.delete(faq);
    log.info("[SchoolGuide] FAQ 삭제. faqId={}", faqId);
  }
}
