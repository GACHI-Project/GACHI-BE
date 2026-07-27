package com.gachi.be.domain.newsletter.repository;

import com.gachi.be.domain.newsletter.entity.NewsletterCulturalGuide;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsletterCulturalGuideRepository
    extends JpaRepository<NewsletterCulturalGuide, Long> {

  /** 특정 가정통신문의 문화 맥락 안내 매핑을 노출 순서대로 조회. */
  List<NewsletterCulturalGuide> findAllByNewsletterIdOrderByDisplayOrderAsc(Long newsletterId);

  /** 특정 가정통신문의 매핑 전체 삭제 (재분석 시 사용). */
  void deleteByNewsletterId(Long newsletterId);
}
