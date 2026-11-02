package com.moviepick.domain.movie.batch.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(name = "movie.batch.recovery.execution-id")
public class MovieBatchRecoveryRunner implements ApplicationRunner {

    private static final String JOB_NAME = "initialMovieSyncJob";

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final long executionId;
    private final boolean confirm;

    public MovieBatchRecoveryRunner(
            JobRepository jobRepository,
            JobOperator jobOperator,
            @Value("${movie.batch.recovery.execution-id}") long executionId,
            @Value("${movie.batch.recovery.confirm:false}") boolean confirm
    ) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.executionId = executionId;
        this.confirm = confirm;
    }

    @Override
    public void run(ApplicationArguments args) {

        // 복구 대상 실행 이력 조회
        JobExecution execution = jobRepository.getJobExecution(executionId);

        if (execution == null) {
            throw new IllegalArgumentException(
                    "Batch 실행 이력이 없습니다: " + executionId
            );
        }

        // 초기 영화 수집 Job만 복구 허용
        String jobName = execution.getJobInstance().getJobName();

        if (!JOB_NAME.equals(jobName)) {
            throw new IllegalArgumentException(
                    "복구 대상 Job이 아닙니다: " + jobName
            );
        }

        log.info(
                "Batch 복구 대상 확인: executionId={}, jobName={}, status={}",
                executionId, jobName, execution.getStatus()
        );

        if (execution.getStatus() != BatchStatus.STARTED) {
            log.warn("STARTED 상태가 아니므로 복구를 건너뜁니다.");
            return;
        }

        // 기본값은 조회 전용
        if (!confirm) {
            log.info("복구 사전 확인 완료. DB 상태는 변경하지 않았습니다.");
            return;
        }

        // 비정상 종료된 실행을 FAILED 상태로 복구
        JobExecution recovered = jobOperator.recover(execution);

        log.info(
                "Batch 복구 완료: executionId={}, status={}",
                recovered.getId(), recovered.getStatus()
        );

        if (recovered.getStatus() != BatchStatus.FAILED) {
            throw new IllegalStateException(
                    "Batch 복구 결과 확인 필요: " + recovered.getStatus()
            );
        }
    }
}
