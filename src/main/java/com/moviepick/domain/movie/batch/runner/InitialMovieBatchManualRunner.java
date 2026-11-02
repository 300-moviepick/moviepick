package com.moviepick.domain.movie.batch.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(
        name = "movie.batch.initial-run",
        havingValue = "true"
)
public class InitialMovieBatchManualRunner implements ApplicationRunner {

    private final JobOperator jobOperator;
    private final Job initialMovieSyncJob;

    public InitialMovieBatchManualRunner(
            JobOperator jobOperator,
            @Qualifier("initialMovieSyncJob") Job initialMovieSyncJob
    ) {
        this.jobOperator = jobOperator;
        this.initialMovieSyncJob = initialMovieSyncJob;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        log.info("TMDB 초기 대량 수집 Job 실행 요청");

        // 실행마다 고유한 JobParameters 생성
        var parameters = new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        // 초기 대량 수집 Job 실행
        JobExecution execution =
                jobOperator.start(initialMovieSyncJob, parameters);

        log.info(
                "초기 대량 수집 종료: executionId={}, status={}",
                execution.getId(),
                execution.getStatus()
        );

        // Batch 실패 시 실행 오류 확인
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException(
                    "초기 영화 수집 Batch 실패: "
                            + execution.getStatus()
            );
        }
    }
}
