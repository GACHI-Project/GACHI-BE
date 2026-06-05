package com.gachi.be.domain.schoolguide.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.gachi.be.domain.schoolguide.entity.SchoolGuide;
import com.gachi.be.domain.schoolguide.entity.enums.SchoolGuideCategory;
import com.gachi.be.domain.schoolguide.repository.SchoolGuideRepository;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(SchoolGuideWeeklyResetSchedulerTest.TestConfig.class)
class SchoolGuideWeeklyResetSchedulerTest {

  @Autowired private SchoolGuideWeeklyResetScheduler scheduler;
  @Autowired private SchoolGuideRepository schoolGuideRepository;

  @BeforeEach
  void setUp() {
    reset(schoolGuideRepository);
  }

  @Test
  @DisplayName("cron 표현식이 매주 월요일 00:00 KST로 설정되어 있다")
  void cronExpressionIsEveryMondayAtMidnightKst() throws Exception {
    Method method =
        SchoolGuideWeeklyResetScheduler.class.getDeclaredMethod("resetWeeklyViewCounts");

    Scheduled scheduled = method.getAnnotation(Scheduled.class);

    assertThat(scheduled).isNotNull();
    assertThat(scheduled.cron()).isEqualTo("0 0 0 * * MON");
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
  }

  @Test
  @DisplayName("resetWeeklyViewCounts 호출 시 repository의 resetAllWeeklyViewCounts가 실행된다")
  void resetWeeklyViewCountsDelegatesToRepository() {
    scheduler.resetWeeklyViewCounts();

    verify(schoolGuideRepository).resetAllWeeklyViewCounts();
    verifyNoMoreInteractions(schoolGuideRepository);
  }

  @Test
  @DisplayName("SchoolGuide 엔티티의 resetWeeklyViewCount 호출 시 조회수가 0이 된다")
  void entityResetWeeklyViewCountSetsToZero() {
    SchoolGuide faq =
        SchoolGuide.builder()
            .category(SchoolGuideCategory.DOCUMENTS)
            .question("테스트 질문")
            .answer("테스트 답변")
            .build();

    // 조회수를 5로 설정
    ReflectionTestUtils.setField(faq, "weeklyViewCount", 5L);
    assertThat(faq.getWeeklyViewCount()).isEqualTo(5L);

    faq.resetWeeklyViewCount();

    assertThat(faq.getWeeklyViewCount()).isEqualTo(0L);
  }

  @Test
  @DisplayName("incrementWeeklyViewCount 호출 시 조회수가 1씩 증가한다")
  void entityIncrementWeeklyViewCountIncreasesByOne() {
    SchoolGuide faq =
        SchoolGuide.builder()
            .category(SchoolGuideCategory.DOCUMENTS)
            .question("테스트 질문")
            .answer("테스트 답변")
            .build();

    assertThat(faq.getWeeklyViewCount()).isEqualTo(0L);

    faq.incrementWeeklyViewCount();
    assertThat(faq.getWeeklyViewCount()).isEqualTo(1L);

    faq.incrementWeeklyViewCount();
    assertThat(faq.getWeeklyViewCount()).isEqualTo(2L);
  }

  @Test
  @DisplayName("조회수가 증가한 여러 FAQ를 초기화하면 모두 0이 된다")
  void resetAfterMultipleIncrementsResultsInZero() {
    List<SchoolGuide> faqs =
        List.of(
            buildFaqWithViewCount(SchoolGuideCategory.DOCUMENTS, 10L),
            buildFaqWithViewCount(SchoolGuideCategory.MEALS, 25L),
            buildFaqWithViewCount(SchoolGuideCategory.ADMISSION, 3L));

    faqs.forEach(SchoolGuide::resetWeeklyViewCount);

    assertThat(faqs).allMatch(faq -> faq.getWeeklyViewCount() == 0L);
  }

  private SchoolGuide buildFaqWithViewCount(SchoolGuideCategory category, long viewCount) {
    SchoolGuide faq = SchoolGuide.builder().category(category).question("질문").answer("답변").build();
    ReflectionTestUtils.setField(faq, "weeklyViewCount", viewCount);
    return faq;
  }

  @Configuration
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    SchoolGuideWeeklyResetScheduler schoolGuideWeeklyResetScheduler(
        SchoolGuideRepository schoolGuideRepository) {
      return new SchoolGuideWeeklyResetScheduler(schoolGuideRepository);
    }

    @Bean
    SchoolGuideRepository schoolGuideRepository() {
      return mock(SchoolGuideRepository.class);
    }

    @Bean
    NoopTransactionManager transactionManager() {
      return new NoopTransactionManager();
    }
  }

  /** 트랜잭션 없이 순수 로직만 테스트하기 위한 No-op 트랜잭션 매니저 */
  static class NoopTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
