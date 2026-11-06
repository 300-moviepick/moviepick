package com.moviepick.domain.ai.recommendation.scheduler;

import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingBulkSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "movie.embedding.schedule",
        name = "enabled",
        havingValue = "true"
)
public class AiMovieEmbeddingScheduledSync {

    private final AiMovieEmbeddingBulkSyncService bulkSyncService;
    private final int batchSize;
    private final int maxMovies;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public AiMovieEmbeddingScheduledSync(
            AiMovieEmbeddingBulkSyncService bulkSyncService,
            @Value("${movie.embedding.schedule.batch-size:50}")
            int batchSize,
            @Value("${movie.embedding.schedule.max-movies:10000}")
            int maxMovies
    ) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException(
                    "batchSize는 1~100이어야 합니다."
            );
        }

        if (maxMovies < 1) {
            throw new IllegalArgumentException(
                    "maxMovies는 1 이상이어야 합니다."
            );
        }

        this.bulkSyncService = bulkSyncService;
        this.batchSize = batchSize;
        this.maxMovies = maxMovies;
    }

    @Scheduled(
            cron = "${movie.embedding.schedule.cron:0 30 4 * * *}",
            zone = "${movie.embedding.schedule.zone:Asia/Seoul}"
    )
    public void synchronize() {

        // 동일 애플리케이션의 중복 실행 방지
        if (!running.compareAndSet(false, true)) {
            log.warn("영화 임베딩 동기화가 이미 실행 중입니다.");
            return;
        }

        try {
            log.info(
                    "영화 임베딩 자동 동기화 시작. batchSize={}, maxMovies={}",
                    batchSize,
                    maxMovies
            );

            // 기존 전체 임베딩 동기화 로직 재사용
            AiMovieEmbeddingBulkSyncService.BulkSyncResult result =
                    bulkSyncService.sync(
                            batchSize,
                            maxMovies
                    );

            log.info(
                    "영화 임베딩 자동 동기화 완료. " +
                            "batches={}, total={}, created={}, " +
                            "updated={}, skipped={}, failed={}, lastMovieId={}",
                    result.batches(),
                    result.total(),
                    result.created(),
                    result.updated(),
                    result.skipped(),
                    result.failed(),
                    result.lastMovieId()
            );

            // 일부 영화가 실패한 경우에도 실패로 기록
            if (result.failed() > 0) {
                throw new IllegalStateException(
                        "영화 임베딩 자동 동기화 실패 건수: "
                                + result.failed()
                );
            }

        } catch (RuntimeException e) {
            log.error(
                    "영화 임베딩 자동 동기화 중 오류 발생",
                    e
            );
            throw e;

        } finally {
            running.set(false);
        }
    }
}