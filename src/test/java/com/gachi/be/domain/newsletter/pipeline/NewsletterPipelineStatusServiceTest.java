package com.gachi.be.domain.newsletter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.entity.enums.NewsletterStatus;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.notification.service.NotificationCreateCommand;
import com.gachi.be.domain.notification.service.NotificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringJUnitConfig(NewsletterPipelineStatusServiceTest.TestConfig.class)
class NewsletterPipelineStatusServiceTest {

  @Autowired private NewsletterPipelineStatusService newsletterPipelineStatusService;
  @Autowired private NewsletterRepository newsletterRepository;
  @Autowired private NotificationService notificationService;
  @Autowired private ChildRepository childRepository;
  @Autowired private CapturingTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    reset(newsletterRepository, notificationService, childRepository);
    transactionManager.reset();
  }

  @Test
  void markCompletedCreatesAnalysisNotificationInNewTransactionAfterCommit() {
    Newsletter newsletter = processingNewsletter(88L);
    when(newsletterRepository.findById(88L)).thenReturn(Optional.of(newsletter));
    when(newsletterRepository.save(newsletter)).thenReturn(newsletter);

    assertThat(AopUtils.isAopProxy(newsletterPipelineStatusService)).isTrue();
    newsletterPipelineStatusService.markCompleted(
        88L, "ocr text", "original text", null, "newsletter title", "summary");

    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.COMPLETED);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);
    verify(notificationService).createNotification(anyLong(), commandCaptor.capture());
    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.type()).isEqualTo(NotificationType.NEWSLETTER_ANALYSIS);
    assertThat(command.level()).isEqualTo(NotificationLevel.IMPORTANT);
    assertThat(command.payload()).containsEntry("newsletterId", 88L);
    assertThat(command.dedupeKey()).isEqualTo("newsletter-analysis:88");
    assertThat(transactionManager.requiresNewCount()).isEqualTo(2);
    assertThat(transactionManager.commits).isGreaterThanOrEqualTo(2);
  }

  @Test
  void markCompletedKeepsCompletedStateWhenNotificationCreationFailsAfterCommit() {
    Newsletter newsletter = processingNewsletter(89L);
    when(newsletterRepository.findById(89L)).thenReturn(Optional.of(newsletter));
    when(newsletterRepository.save(newsletter)).thenReturn(newsletter);
    doThrow(new IllegalStateException("push event failure"))
        .when(notificationService)
        .createNotification(anyLong(), any());

    assertThatCode(
            () ->
                newsletterPipelineStatusService.markCompleted(
                    89L, "ocr text", "original text", null, "newsletter title", "summary"))
        .doesNotThrowAnyException();

    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.COMPLETED);
    verify(notificationService).createNotification(anyLong(), any());
    assertThat(transactionManager.requiresNewCount()).isEqualTo(2);
    assertThat(transactionManager.commits).isGreaterThanOrEqualTo(1);
    assertThat(transactionManager.rollbacks).isGreaterThanOrEqualTo(1);
  }

  @Test
  void markFailedIfContentDuplicatedStoresHashAndStopsPipeline() {
    Newsletter newsletter = processingNewsletter(90L);
    when(newsletterRepository.findById(90L)).thenReturn(Optional.of(newsletter));
    when(newsletterRepository.existsDuplicateContentHash(anyLong(), anyLong(), any(), any(), any()))
        .thenReturn(true);

    boolean duplicated =
        newsletterPipelineStatusService.markFailedIfContentDuplicated(
            90L, "ocr text", "original text", "content-hash");

    assertThat(duplicated).isTrue();
    assertThat(newsletter.getContentHash()).isEqualTo("content-hash");
    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.FAILED);
    assertThat(newsletter.getFailureStage()).isEqualTo("CONTENT_DUPLICATE");
    assertThat(newsletter.getFailureReason()).contains("동일한 내용");
    verify(newsletterRepository).save(newsletter);
  }

  @Test
  void markFailedIfContentDuplicatedOnlyStoresHashWhenUnique() {
    Newsletter newsletter = processingNewsletter(91L);
    when(newsletterRepository.findById(91L)).thenReturn(Optional.of(newsletter));
    when(newsletterRepository.existsDuplicateContentHash(anyLong(), anyLong(), any(), any(), any()))
        .thenReturn(false);

    boolean duplicated =
        newsletterPipelineStatusService.markFailedIfContentDuplicated(
            91L, "ocr text", "original text", "content-hash");

    assertThat(duplicated).isFalse();
    assertThat(newsletter.getContentHash()).isEqualTo("content-hash");
    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.PROCESSING);
    assertThat(newsletter.getFailureStage()).isNull();
    verify(newsletterRepository).save(newsletter);
  }

  @Test
  void markFailedIfContentDuplicatedComparesLegacyOriginalTextWhenContentHashIsMissing() {
    Newsletter newsletter = processingNewsletter(92L);
    Newsletter legacy = processingNewsletter(93L);
    legacy.complete(
        "ocr", "와글와글 베이커리 신청 안내\n제출 기한: 2026.06.20\n준비물: 앞치마", null, "와글와글 베이커리", "summary");
    when(newsletterRepository.findById(92L)).thenReturn(Optional.of(newsletter));
    when(newsletterRepository.existsDuplicateContentHash(anyLong(), anyLong(), any(), any(), any()))
        .thenReturn(false);
    when(newsletterRepository.findLegacyContentHashCandidates(anyLong(), anyLong(), any(), any()))
        .thenReturn(List.of(legacy));

    String contentHash =
        new NewsletterContentHasher()
            .hash("와글와글 베이커리 신청 안내 - 제출 기한 2026-06-20 / 준비물 앞치마")
            .orElseThrow();

    boolean duplicated =
        newsletterPipelineStatusService.markFailedIfContentDuplicated(
            92L, "ocr text", "original text", contentHash);

    assertThat(duplicated).isTrue();
    assertThat(newsletter.getStatus()).isEqualTo(NewsletterStatus.FAILED);
    assertThat(newsletter.getFailureStage()).isEqualTo("CONTENT_DUPLICATE");
  }

  private Newsletter processingNewsletter(Long id) {
    Newsletter newsletter =
        Newsletter.builder()
            .userId(4L)
            .fileKey("newsletter/test.png")
            .fileHash("hash-newsletter-complete-" + id)
            .status(NewsletterStatus.PROCESSING)
            .language("KO")
            .build();
    ReflectionTestUtils.setField(newsletter, "id", id);
    return newsletter;
  }

  @Configuration
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    NewsletterPipelineStatusService newsletterPipelineStatusService(
        NewsletterRepository newsletterRepository,
        NotificationService notificationService,
        ChildRepository childRepository,
        CapturingTransactionManager transactionManager,
        NewsletterContentHasher newsletterContentHasher) {
      return new NewsletterPipelineStatusService(
          newsletterRepository,
          notificationService,
          childRepository,
          transactionManager,
          newsletterContentHasher);
    }

    @Bean
    NewsletterRepository newsletterRepository() {
      return mock(NewsletterRepository.class);
    }

    @Bean
    NotificationService notificationService() {
      return mock(NotificationService.class);
    }

    @Bean
    ChildRepository childRepository() {
      return mock(ChildRepository.class);
    }

    @Bean
    CapturingTransactionManager transactionManager() {
      return new CapturingTransactionManager();
    }

    @Bean
    NewsletterContentHasher newsletterContentHasher() {
      return new NewsletterContentHasher();
    }
  }

  static class CapturingTransactionManager extends AbstractPlatformTransactionManager {
    private final List<TransactionDefinition> definitions = new ArrayList<>();
    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      definitions.add(definition);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }

    private List<Integer> propagationBehaviors() {
      return definitions.stream().map(TransactionDefinition::getPropagationBehavior).toList();
    }

    private long requiresNewCount() {
      return propagationBehaviors().stream()
          .filter(behavior -> behavior == TransactionDefinition.PROPAGATION_REQUIRES_NEW)
          .count();
    }

    private void reset() {
      definitions.clear();
      commits = 0;
      rollbacks = 0;
    }
  }
}
