package com.moviepick.domain.ai.recommendation.runner;

import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingBulkSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "movie.embedding.bulk-sync.enabled",
        havingValue = "true"
)
public class AiMovieEmbeddingBulkSyncRunner implements ApplicationRunner {

    private final AiMovieEmbeddingBulkSyncService bulkSyncService;

    @Value("${movie.embedding.bulk-sync.batch-size:50}")
    private int batchSize;

    @Value("${movie.embedding.bulk-sync.max-movies:2000}")
    private int maxMovies;

    @Override
    public void run(ApplicationArguments args) {

        log.info(
                "전체 영화 임베딩 수집 시작. batchSize={}, maxMovies={}",
                batchSize,
                maxMovies
        );

        AiMovieEmbeddingBulkSyncService.BulkSyncResult result =
                bulkSyncService.sync(
                        batchSize,
                        maxMovies
                );

        log.info(
                "전체 영화 임베딩 수집 완료. batches={}, total={}, created={}, updated={}, skipped={}, failed={}, lastMovieId={}",
                result.batches(),
                result.total(),
                result.created(),
                result.updated(),
                result.skipped(),
                result.failed(),
                result.lastMovieId()
        );

        if (result.failed() > 0) {
            throw new IllegalStateException(
                    "영화 임베딩 수집 중 "
                            + result.failed()
                            + "건 실패했습니다."
            );
        }
    }
}