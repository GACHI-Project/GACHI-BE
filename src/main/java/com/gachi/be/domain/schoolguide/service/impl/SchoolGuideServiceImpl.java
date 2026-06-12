package com.gachi.be.domain.schoolguide.service.impl;

import com.gachi.be.domain.newsletter.pipeline.PapagoTranslateClient;
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
import com.gachi.be.domain.user.entity.User;
import com.gachi.be.domain.user.repository.UserRepository;
import com.gachi.be.global.code.ErrorCode;
import com.gachi.be.global.exception.BusinessException;
import com.gachi.be.global.util.I18nTextResolver;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
  private final UserRepository userRepository;
  private final PapagoTranslateClient papagoTranslateClient;

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
  public SchoolGuidePopularResponse getPopularFaqs(Long userId) {
    List<SchoolGuide> faqs = schoolGuideRepository.findTop2ByOrderByWeeklyViewCountDesc();
    String language = resolveUserLanguage(userId);
    return SchoolGuidePopularResponse.of(faqs, language);
  }

  /** FAQ 목록 조회 (카테고리 필터 or 검색). */
  @Override
  @Transactional(readOnly = true)
  public SchoolGuideListResponse getFaqs(Long userId, SchoolGuideCategory category, String search) {

    List<SchoolGuide> faqs;

    if (category != null) {
      faqs = schoolGuideRepository.findByCategoryOrderByCreatedAtAsc(category);
    } else if (search != null && !search.isBlank()) {
      faqs = schoolGuideRepository.findByQuestionContainingIgnoreCaseOrderByCreatedAtAsc(search);
    } else {
      faqs = schoolGuideRepository.findAllByOrderByCreatedAtAsc();
    }

    String language = resolveUserLanguage(userId);
    return SchoolGuideListResponse.of(faqs, language);
  }

  /** FAQ 상세 조회 + weekly_view_count 증가. */
  @Override
  @Transactional
  public SchoolGuideDetailResponse getFaqDetail(Long userId, Long faqId) {

    SchoolGuide faq =
        schoolGuideRepository
            .findById(faqId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_GUIDE_NOT_FOUND));

    faq.incrementWeeklyViewCount();

    log.debug("[SchoolGuide] 상세 조회. faqId={}, weeklyViewCount={}", faqId, faq.getWeeklyViewCount());

    String language = resolveUserLanguage(userId);
    return SchoolGuideDetailResponse.of(faq, language);
  }

  /** FAQ 등록. */
  @Override
  @Transactional
  public Long createFaq(SchoolGuideCreateRequest request) {

    Map<String, String> questionI18n = translateToAllLanguages(request.getQuestion());
    Map<String, String> answerI18n = translateToAllLanguages(request.getAnswer());

    SchoolGuide faq =
        SchoolGuide.builder()
            .category(request.getCategory())
            .question(request.getQuestion())
            .questionI18n(questionI18n)
            .answer(request.getAnswer())
            .answerI18n(answerI18n)
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
    if (request.getQuestion() != null && !request.getQuestion().isBlank()) {
      faq.updateQuestion(request.getQuestion(), translateToAllLanguages(request.getQuestion()));
    }
    if (request.getAnswer() != null && !request.getAnswer().isBlank()) {
      faq.updateAnswer(request.getAnswer(), translateToAllLanguages(request.getAnswer()));
    }
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

  private String resolveUserLanguage(Long userId) {
    return userRepository
        .findById(userId)
        .map(User::getLanguageCode)
        .filter(code -> code != null && !code.isBlank())
        .orElse(I18nTextResolver.DEFAULT_LANGUAGE);
  }

  private Map<String, String> translateToAllLanguages(String koText) {
    Map<String, String> result = new LinkedHashMap<>();
    if (koText == null || koText.isBlank()) {
      return result;
    }
    for (String lang : I18nTextResolver.SUPPORTED_LANGUAGES) {
      if ("KO".equals(lang)) {
        result.put(lang, koText);
        continue;
      }
      String translated = papagoTranslateClient.translate(koText, lang);
      result.put(lang, translated != null && !translated.isBlank() ? translated : koText);
    }
    return result;
  }
}
