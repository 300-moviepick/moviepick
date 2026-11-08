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
public class MovieSyncTasklet implements Tasklet {

    private final MovieCollectionService movieCollectionService;
    private final int collectPages;

    @Override
    public RepeatStatus execute(
            StepContribution contribution,
            ChunkContext chunkContext
    ) {
        log.info("TMDB 영화 수집 배치 시작");

        // 1. 기존 영화 수집 서비스 호출
        MovieSyncResponse result = movieCollectionService.collectPopularMoviesWithResult(collectPages);

        // 2. 수집 결과 로그
        log.info(
                "영화 수집 결과: 저장={}, 실패={}, 중단 페이지={}, 사유={}",
                result.savedCount(),
                result.failedCount(),
                result.stoppedPage(),
                result.stopReason()
        );

        // 3. 일부 실패 또는 중단이 발생하면 Batch 실패 처리
        if (result.hasFailure()) {
            throw new IllegalStateException(
                    "TMDB 영화 수집 실패: " + result
            );
        }

        // 4. 정상적으로 수집된 경우
        log.info("TMDB 영화 수집 배치 완료");

        return RepeatStatus.FINISHED;
    }
}