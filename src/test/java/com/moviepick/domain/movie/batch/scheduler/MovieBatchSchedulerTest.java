package com.moviepick.domain.movie.batch.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovieBatchSchedulerTest {

    @Test
    void syncMoviesSuccess() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobRepository repository = mock(JobRepository.class);
        JobExecution execution = mock(JobExecution.class);

        when(repository.findRunningJobExecutions("movieSyncJob"))
                .thenReturn(Collections.emptySet());

        when(operator.start(eq(job), any(JobParameters.class)))
                .thenReturn(execution);

        when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);

        MovieBatchScheduler scheduler =
                new MovieBatchScheduler(operator, job, repository);

        scheduler.syncMovies();

        ArgumentCaptor<JobParameters> captor =
                ArgumentCaptor.forClass(JobParameters.class);

        verify(operator).start(eq(job), captor.capture());

        assertNotNull(captor.getValue().getString("run.id"));
    }

    @Test
    void syncMoviesSkipWhenRunning() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobRepository repository = mock(JobRepository.class);

        // 이미 작업이 실행 중인 상황
        when(repository.findRunningJobExecutions("movieSyncJob"))
                .thenReturn(Set.of(mock(JobExecution.class)));

        MovieBatchScheduler scheduler =
                new MovieBatchScheduler(operator, job, repository);

        scheduler.syncMovies();

        // 새로운 Job이 실행되지 않아야 함
        verifyNoInteractions(operator);
    }

    @Test
    void syncMoviesSkipWhenAlreadyCompleted() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        Job job = mock(Job.class);
        JobRepository repository = mock(JobRepository.class);

        when(repository.findRunningJobExecutions("movieSyncJob"))
                .thenReturn(Collections.emptySet());

        when(operator.start(eq(job), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException(
                        "이미 완료된 작업"
                ));

        MovieBatchScheduler scheduler =
                new MovieBatchScheduler(operator, job, repository);

        // 이미 완료된 작업은 예외를 처리하고 건너뛰어야 함
        assertDoesNotThrow(scheduler::syncMovies);

        verify(operator, times(1))
                .start(eq(job), any(JobParameters.class));
    }
}
