package com.moviepick.domain.movie.batch.service;

import com.moviepick.domain.movie.batch.dto.MovieBatchRunResponse;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class MovieBatchManualService {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final Job movieSyncJob;

    public MovieBatchManualService(
            JobOperator jobOperator,
            JobRepository jobRepository,
            @Qualifier("movieSyncJob") Job movieSyncJob
    ) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.movieSyncJob = movieSyncJob;
    }

    public synchronized MovieBatchRunResponse runSync() {
        String jobName = movieSyncJob.getName();

        // 이미 실행 중인 동일 Job 확인
        if (!jobRepository.findRunningJobExecutions(jobName).isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.CONCURRENT_REQUEST
            );
        }

        JobParameters parameters = new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();

        try {
            JobExecution execution =
                    jobOperator.start(movieSyncJob, parameters);

            log.info(
                    "관리자 영화 배치 실행: executionId={}, status={}",
                    execution.getId(),
                    execution.getStatus()
            );

            return new MovieBatchRunResponse(
                    execution.getId(),
                    jobName,
                    execution.getStatus()
            );

        } catch (JobExecutionAlreadyRunningException e) {
            throw new BusinessException(
                    CommonErrorCode.CONCURRENT_REQUEST
            );

        } catch (JobInstanceAlreadyCompleteException
                 | InvalidJobParametersException
                 | JobRestartException e) {

            log.error("관리자 영화 배치 실행 오류", e);

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR
            );
        }
    }
}
