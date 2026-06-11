package com.gachi.be.domain.newsletter.pipeline;

import com.gachi.be.domain.child.repository.ChildRepository;
import com.gachi.be.domain.newsletter.entity.Newsletter;
import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import com.gachi.be.domain.notification.entity.enums.NotificationLevel;
import com.gachi.be.domain.notification.entity.enums.NotificationType;
import com.gachi.be.domain.notification.service.NotificationCreateCommand;
import com.gachi.be.domain.notification.service.NotificationService;
import com.gachi.be.domain.notification.service.NotificationTemplateKey;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsletterPipelineStatusService {

  private final NewsletterRepository newsletterRepository;
  private final NotificationService notificationService;
  private final ChildRepository childRepository;
  private final PlatformTransactionManager transactionManager;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markProcessing(Long newsletterId) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            newsletter -> {
              newsletter.startProcessing();
              newsletterRepository.save(newsletter);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markCompleted(
      Long newsletterId,
      String ocrText,
      String originalText,
      String translatedText,
      String title,
      String summary) {
    markCompleted(newsletterId, ocrText, originalText, translatedText, title, Map.of(), summary);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markCompleted(
      Long newsletterId,
      String ocrText,
      String originalText,
      String translatedText,
      String title,
      Map<String, String> titleI18n,
      String summary) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            newsletter -> {
              newsletter.complete(ocrText, originalText, translatedText, title, titleI18n, summary);
              Newsletter saved = newsletterRepository.save(newsletter);
              scheduleAnalysisCompletedNotification(saved);
            });
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailedWithSnapshot(
      Long newsletterId,
      String ocrText,
      String originalText,
      String translatedText,
      String failureStage,
      String failureReason) {
    newsletterRepository
        .findById(newsletterId)
        .ifPresent(
            newsletter -> {
              newsletter.failWithSnapshot(
                  ocrText, originalText, translatedText, failureStage, failureReason);
              newsletterRepository.save(newsletter);
            });
  }

  private void scheduleAnalysisCompletedNotification(Newsletter newsletter) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              createAnalysisCompletedNotificationSafely(newsletter);
            }
          });
      return;
    }
    createAnalysisCompletedNotificationSafely(newsletter);
  }

  private void createAnalysisCompletedNotificationSafely(Newsletter newsletter) {
    try {
      createAnalysisCompletedNotificationInNewTransaction(newsletter);
    } catch (Exception ex) {
      log.warn(
          "[Pipeline] 분석 완료 알림 생성 실패. newsletterId={}, error={}",
          newsletter.getId(),
          ex.getMessage(),
          ex);
    }
  }

  private void createAnalysisCompletedNotificationInNewTransaction(Newsletter newsletter) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    // afterCommit 콜백 안에서는 기존 트랜잭션 리소스가 남아 알림 저장과 이벤트 커밋 경계가 꼬일 수 있습니다.
    transactionTemplate.executeWithoutResult(
        status -> createAnalysisCompletedNotification(newsletter));
  }

  private void createAnalysisCompletedNotification(Newsletter newsletter) {
    Long childId = resolveChildId(newsletter);
    notificationService.createNotification(
        newsletter.getUserId(),
        new NotificationCreateCommand(
            NotificationType.NEWSLETTER_ANALYSIS,
            "새 가정통신문 분석 완료",
            newsletter.getTitle() != null && !newsletter.getTitle().isBlank()
                ? newsletter.getTitle() + " 분석이 완료되었어요"
                : "가정통신문 분석이 완료되었어요",
            Map.of(
                "newsletterId",
                newsletter.getId(),
                "childName",
                newsletter.getChildName() != null ? newsletter.getChildName() : ""),
            NotificationTemplateKey.NEWSLETTER_ANALYSIS,
            Map.of(
                "newsletterTitle",
                newsletter.getTitle() != null ? newsletter.getTitle() : "",
                "newsletterTitleI18n",
                newsletter.getTitleI18n() != null ? newsletter.getTitleI18n() : Map.of()),
            "newsletter-analysis:" + newsletter.getId(),
            NotificationLevel.IMPORTANT,
            childId,
            newsletter.getChildName()));
  }

  private Long resolveChildId(Newsletter newsletter) {
    if (newsletter.getChildName() == null || newsletter.getChildName().isBlank()) {
      return null;
    }
    return childRepository
        .findFirstByUserIdAndNameAndDeletedAtIsNull(
            newsletter.getUserId(), newsletter.getChildName())
        .map(child -> child.getId())
        .orElse(null);
  }
}
