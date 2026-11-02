package com.moviepick.domain.movie.batch;

import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import com.moviepick.domain.movie.service.MovieCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "tmdb.access-token=test-dummy-token",
        "movie.batch.initial-pages=100",
        "movie.batch.scheduler.enabled=false"
})
class InitialMovieBatchRunTest {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    @Qualifier("initialMovieSyncJob")
    private Job initialMovieSyncJob;

    // 실제 TMDB 수집 서비스 대신 Mock 사용
    @MockitoBean
    private MovieCollectionService movieCollectionService;

    // 테스트별 고유한 Job 실행 파라미터
    private JobParameters createJobParameters() {
        return new JobParametersBuilder()
                .addString("run.id", UUID.randomUUID().toString())
                .toJobParameters();
    }

    @Test
    void initialMovieSyncJobSuccess() throws Exception {

        // 100페이지, 영화 2,000편 수집 성공 가정
        when(movieCollectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 2000, 0));

        // 초기 대량 수집 Job 실행
        JobExecution execution =
                jobOperator.start(initialMovieSyncJob, createJobParameters());

        // Batch 성공 여부 검증
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        verify(movieCollectionService, times(1))
                .collectInitialPopularMoviesWithResult(100);
    }

    @Test
    void initialMovieSyncJobFail() throws Exception {

        // 영화 1,990편 성공, 10편 실패 가정
        when(movieCollectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 1990, 10));

        JobExecution execution =
                jobOperator.start(initialMovieSyncJob, createJobParameters());

        // 일부 수집 실패 시 Batch 실패 처리 확인
        assertEquals(BatchStatus.FAILED, execution.getStatus());

        verify(movieCollectionService, times(1))
                .collectInitialPopularMoviesWithResult(100);
    }
}