package com.moviepick.domain.movie.batch.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.DefaultApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieBatchRecoveryRunnerTest {

    private static final long EXECUTION_ID = 7L;
    private static final String JOB_NAME = "initialMovieSyncJob";

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobOperator jobOperator;

    @Test
    void recoverStartedExecutionWhenConfirmed() {
        JobExecution started = createExecution(JOB_NAME, BatchStatus.STARTED);
        JobExecution recovered = createExecution(JOB_NAME, BatchStatus.FAILED);

        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(started);
        when(jobOperator.recover(started)).thenReturn(recovered);

        createRunner(true).run(new DefaultApplicationArguments());

        verify(jobOperator).recover(started);
    }

    @Test
    void skipRecoveryWithoutConfirmation() {
        JobExecution started = createExecution(JOB_NAME, BatchStatus.STARTED);

        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(started);

        createRunner(false).run(new DefaultApplicationArguments());

        verifyNoInteractions(jobOperator);
    }

    @Test
    void skipRecoveryWhenAlreadyFailed() {
        JobExecution failed = createExecution(JOB_NAME, BatchStatus.FAILED);

        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(failed);

        createRunner(true).run(new DefaultApplicationArguments());

        verifyNoInteractions(jobOperator);
    }

    @Test
    void rejectRecoveryForAnotherJob() {
        JobExecution otherJob = createExecution(
                "movieSyncJob",
                BatchStatus.STARTED
        );

        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(otherJob);

        assertThrows(IllegalArgumentException.class,
                () -> createRunner(true).run(new DefaultApplicationArguments())
        );

        verifyNoInteractions(jobOperator);
    }

    @Test
    void rejectRecoveryWhenExecutionNotFound() {
        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> createRunner(true).run(new DefaultApplicationArguments())
        );

        verifyNoInteractions(jobOperator);
    }

    @Test
    void failWhenRecoveredStatusIsNotFailed() {
        JobExecution started = createExecution(JOB_NAME, BatchStatus.STARTED);
        JobExecution unexpected = createExecution(
                JOB_NAME,
                BatchStatus.STARTED
        );

        when(jobRepository.getJobExecution(EXECUTION_ID)).thenReturn(started);
        when(jobOperator.recover(started)).thenReturn(unexpected);

        assertThrows(IllegalStateException.class,
                () -> createRunner(true).run(new DefaultApplicationArguments())
        );

        verify(jobOperator).recover(started);
    }

    private MovieBatchRecoveryRunner createRunner(boolean confirm) {
        return new MovieBatchRecoveryRunner(
                jobRepository,
                jobOperator,
                EXECUTION_ID,
                confirm
        );
    }

    private JobExecution createExecution(String jobName, BatchStatus status) {
        JobInstance instance = new JobInstance(1L, jobName);

        JobExecution execution = new JobExecution(
                EXECUTION_ID,
                instance,
                new JobParameters()
        );

        execution.setStatus(status);
        return execution;
    }
}
