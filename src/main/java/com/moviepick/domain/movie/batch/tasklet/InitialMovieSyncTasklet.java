package com.moviepick.domain.movie.batch.tasklet;

import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import com.moviepick.domain.movie.service.MovieCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@Slf4j
@RequiredArgsConstructor
public class InitialMovieSyncTasklet implements Tasklet {

    private final MovieCollectionService movieCollectionService;
    private final int collectPages;

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        log.info("TMDB 초기 대량 영화 수집 시작: pages={}", collectPages);

        // 초기 대량 수집 서비스 호출
        MovieSyncResponse result =
                movieCollectionService.collectInitialPopularMoviesWithResult(collectPages);

        log.info(
                "초기 영화 수집 결과: 저장={}, 실패={}, 중단 페이지={}, 사유={}",
                result.savedCount(),
                result.failedCount(),
                result.stoppedPage(),
                result.stopReason()
        );

        // 일부 실패 또는 중단 시 Batch 실패 처리
        if (result.failedCount() > 0
                || result.stoppedPage() != null
                || result.stopReason() != null) {
            throw new IllegalStateException(
                    "TMDB 초기 영화 수집 실패: " + result
            );
        }

        log.info("TMDB 초기 대량 영화 수집 완료");

        return RepeatStatus.FINISHED;
    }
}