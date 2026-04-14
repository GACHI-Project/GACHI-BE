package com.gachi.be.domain.checklist.repository;

import com.gachi.be.domain.checklist.entity.ChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

  /** 특정 가정통신문의 체크리스트 항목 전체 조회 (생성 순서대로) */
  List<ChecklistItem> findByNewsletterIdOrderByIdAsc(Long newsletterId);

  /** 특정 가정통신문의 체크리스트 항목 전체 삭제 (재분석 시 사용) */
  void deleteByNewsletterId(Long newsletterId);
}
