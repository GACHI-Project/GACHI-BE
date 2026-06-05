package com.gachi.be.domain.schoolguide.repository;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SchoolGuideRepository extends JpaRepository<SchoolGuide, Long> {

  /** 카테고리별 FAQ 조회 */
  List<SchoolGuide> findByCategoryOrderByCreatedAtAsc(SchoolGuideCategory category);

  /** question 텍스트 검색 */
  List<SchoolGuide> findByQuestionContainingIgnoreCaseOrderByCreatedAtAsc(String keyword);

  /** 카테고리별 개수 집계 */
  @Query("SELECT s.category, COUNT(s) FROM SchoolGuide s GROUP BY s.category")
  List<Object[]> countByCategory();

  /** 주간 조회수 TOP 2 */
  List<SchoolGuide> findTop2ByOrderByWeeklyViewCountDesc();

  /** 매주 월요일 00:00 주간 조회수 일괄 초기화 */
  @Modifying
  @Query("UPDATE SchoolGuide s SET s.weeklyViewCount = 0")
  void resetAllWeeklyViewCounts();
}
