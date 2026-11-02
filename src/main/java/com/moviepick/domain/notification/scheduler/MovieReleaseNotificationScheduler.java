package com.moviepick.domain.notification.scheduler;

import com.moviepick.domain.notification.service.NotificationService;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 매일 09:00(서울)에 오늘 개봉하는 찜 영화의 개봉 알림을 만든다. */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "notification.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MovieReleaseNotificationScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void createTodayReleaseNotifications() {
        LocalDate today = LocalDate.now(ZONE);
        try {
            int created = notificationService.createMovieReleaseNotifications(today);
            log.info("개봉 알림 생성 완료: date={}, created={}", today, created);
        } catch (Exception e) {
            log.error("개봉 알림 생성 실패: date={}", today, e);
        }
    }
}
