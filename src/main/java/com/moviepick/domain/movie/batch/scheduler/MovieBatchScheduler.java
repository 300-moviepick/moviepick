package com.moviepick.domain.movie.batch.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "movie.batch.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class MovieBatchScheduler {

    private static final String JOB_NAME = "movieSyncJob";
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final JobOperator jobOperator;
    private final Job movieSyncJob;
    private final JobRepository jobRepository;

    public MovieBatchScheduler(
            JobOperator jobOperator,
            @Qualifier("movieSyncJob") Job movieSyncJob,
            JobRepository jobRepository
    ) {
        this.jobOperator = jobOperator;
        this.movieSyncJob = movieSyncJob;
        this.jobRepository = jobRepository;
    }

    @Scheduled(
            cron = "${movie.batch.scheduler.cron:0 0 3 * * *}",
            zone = "Asia/Seoul"
    )
    public void syncMovies() {

        // 1. 기존 영화 수집 작업이 실행 중인지 확인
        try {
            if (!jobRepository.findRunningJobExecutions(JOB_NAME).isEmpty()) {
                log.warn("TMDB 영화 수집 작업이 이미 실행 중입니다. 이번 실행을 건너뜁니다.");
                return;
            }

            // 2. 같은 날짜에는 동일한 JobParameters 사용
            String runDate = LocalDate.now(ZONE).toString();

            var parameters = new JobParametersBuilder()
                    .addString("run.id", runDate)
                    .toJobParameters();

            // 3. 영화 수집 Job 실행
            JobExecution execution =
                    jobOperator.start(movieSyncJob, parameters);

            BatchStatus status = execution.getStatus();

            if (status == BatchStatus.COMPLETED) {
                log.info("TMDB 영화 자동 수집 완료: {}", runDate);
            } else if (status.isRunning()) {
                log.info("TMDB 영화 배치 실행 시작: id={}, 상태={}",
                        execution.getId(), status);
            } else {
                log.warn("TMDB 영화 배치 실행 결과: {}", status);
            }

        } catch (JobInstanceAlreadyCompleteException e) {
            log.info("오늘 영화 수집이 이미 완료되어 중복 실행을 건너뜁니다.");

        } catch (JobExecutionAlreadyRunningException e) {
            log.warn("동일한 영화 배치 작업이 이미 실행 중입니다.");

        } catch (Exception e) {
            log.error("TMDB 영화 자동 수집 실행 오류", e);
        }
    }
}
