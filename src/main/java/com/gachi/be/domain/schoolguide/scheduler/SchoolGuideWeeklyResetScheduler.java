package com.gachi.be.domain.schoolguide.scheduler;

import com.gachi.be.domain.schoolguide.repository.SchoolGuideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchoolGuideWeeklyResetScheduler {

    private final SchoolGuideRepository schoolGuideRepository;

    /** 매주 월요일 00:00 (KST) 주간 조회수 초기화 */
    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Seoul")
    @Transactional
    public void resetWeeklyViewCounts() {
        log.info("[SchoolGuide] 주간 조회수 초기화 시작");
        schoolGuideRepository.resetAllWeeklyViewCounts();
        log.info("[SchoolGuide] 주간 조회수 초기화 완료");
    }
}
