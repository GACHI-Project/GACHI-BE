package com.gachi.be.domain.newsletter.scheduler;

import com.gachi.be.domain.newsletter.repository.NewsletterRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멈춘 채(PAUSED) 방치된 가정통신문을 자동으로 FAILED 처리하는 스케줄러.
 *
 * 결정 사항: 목록에서 '이어서 진행'을 보여주되, 멈춘 지 24시간이 지나면 FAILED로 전환한다. 페이지 결과와 원문 스냅샷은 그대로 남으므로 사용자는 이후 '다시
 * 분석'으로 OCR 결과를 재사용해 다시 진행할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsletterPausedExpiryScheduler {

    /** 멈춘 뒤 이 시간이 지나면 FAILED로 전환한다. */
    static final Duration PAUSED_EXPIRY = Duration.ofHours(24);

    static final String EXPIRED_FAILURE_STAGE = "PAUSED_EXPIRED";
    static final String EXPIRED_FAILURE_REASON = "멈춘 뒤 24시간 동안 이어서 진행하지 않아 자동으로 종료되었습니다.";

    private final NewsletterRepository newsletterRepository;
    private final Clock clock;

    /** 매시 정각 (KST) 24시간 넘게 방치된 PAUSED 문서를 FAILED로 전환 */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional
    public void expirePausedNewsletters() {
        OffsetDateTime threshold = OffsetDateTime.now(clock).minus(PAUSED_EXPIRY);
        int expiredCount =
            newsletterRepository.expirePausedBefore(
                threshold, EXPIRED_FAILURE_STAGE, EXPIRED_FAILURE_REASON);
        if (expiredCount > 0) {
            log.info(
                "[NewsletterPausedExpiry] 방치된 PAUSED 문서 FAILED 전환. count={}, threshold={}",
                expiredCount,
                threshold);
        } else {
            log.debug("[NewsletterPausedExpiry] 전환 대상 없음. threshold={}", threshold);
        }
    }
}
