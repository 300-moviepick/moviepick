package com.moviepick.domain.ai.recommendation.runner;

import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingSyncService;
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
        name = "movie.embedding.sync.enabled",
        havingValue = "true"
)
public class AiMovieEmbeddingSyncRunner implements ApplicationRunner {

    private final AiMovieEmbeddingSyncService syncService;

    @Value("${movie.embedding.sync.limit:10}")
    private int limit;

    @Override
    public void run(ApplicationArguments args) {

        log.info(
                "영화 임베딩 수집 시작. limit={}",
                limit
        );

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.sync(limit);

        log.info(
                "영화 임베딩 수집 완료. total={}, created={}, updated={}, skipped={}, failed={}",
                result.total(),
                result.created(),
                result.updated(),
                result.skipped(),
                result.failed()
        );

        if (result.failed() > 0) {
            throw new IllegalStateException(
                    "영화 임베딩 생성 중 "
                            + result.failed()
                            + "건 실패했습니다."
            );
        }
    }
}