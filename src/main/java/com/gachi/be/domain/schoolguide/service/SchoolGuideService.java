package com.gachi.be.domain.schoolguide.service;

import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideCreateRequest;
import com.gachi.be.domain.schoolguide.dto.request.SchoolGuideUpdateRequest;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideCategoryResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideDetailResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuideListResponse;
import com.gachi.be.domain.schoolguide.dto.response.SchoolGuidePopularResponse;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;

public interface SchoolGuideService {

  /** 카테고리별 FAQ 개수 조회 */
  SchoolGuideCategoryResponse getCategoryCounts();

  /** 주간 인기 질문 TOP 2 */
  SchoolGuidePopularResponse getPopularFaqs(Long userId);

  /** FAQ 목록 조회 */
  SchoolGuideListResponse getFaqs(Long userId, SchoolGuideCategory category, String search);

  /** FAQ 상세 조회 + weekly_view_count 증가 */
  SchoolGuideDetailResponse getFaqDetail(Long userId, Long faqId);

  /** FAQ 등록 */
  Long createFaq(SchoolGuideCreateRequest request);

  /** FAQ 수정 */
  void updateFaq(Long faqId, SchoolGuideUpdateRequest request);

  /** FAQ 삭제 */
  void deleteFaq(Long faqId);
}
