package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingBatchReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.ai.model.embedding",
        havingValue = "ollama"
)
public class AiMovieEmbeddingBulkSyncService {

    private static final int MAX_BATCH_SIZE = 100;

    private final AiMovieEmbeddingBatchReader batchReader;
    private final AiMovieEmbeddingSyncService syncService;

    public BulkSyncResult sync(
            int batchSize,
            int maxMovies
    ) {
        if (batchSize < 1
                || batchSize > MAX_BATCH_SIZE
                || maxMovies < 1) {
            throw new IllegalArgumentException(
                    "batchSize는 1~100, maxMovies는 1 이상이어야 합니다."
            );
        }

        long lastMovieId = 0L;

        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int batches = 0;

        while (total < maxMovies) {

            int currentSize = Math.min(
                    batchSize,
                    maxMovies - total
            );

            // ① 이전 페이지의 마지막 영화 ID 이후 조회
            List<AiMovieCandidate> movies =
                    batchReader.findNext(
                            lastMovieId,
                            currentSize
                    );

            if (movies.isEmpty()) {
                break;
            }

            long nextMovieId =
                    movies.get(movies.size() - 1).movieId();

            if (nextMovieId <= lastMovieId) {
                throw new IllegalStateException(
                        "영화 ID 커서가 앞으로 이동하지 않았습니다."
                );
            }

            // ② 해당 페이지 임베딩 생성·저장
            AiMovieEmbeddingSyncService.SyncResult result =
                    syncService.syncMovies(movies);

            // ③ 누적 통계
            total += result.total();
            created += result.created();
            updated += result.updated();
            skipped += result.skipped();
            failed += result.failed();
            batches++;

            // ④ 다음 페이지 커서 갱신
            lastMovieId = nextMovieId;

            log.info(
                    "영화 임베딩 페이지 완료. batch={}, cursor={}, total={}, created={}, updated={}, skipped={}, failed={}",
                    batches,
                    lastMovieId,
                    total,
                    created,
                    updated,
                    skipped,
                    failed
            );
        }

        return new BulkSyncResult(
                batches,
                total,
                created,
                updated,
                skipped,
                failed,
                lastMovieId
        );
    }

    public record BulkSyncResult(
            int batches,
            int total,
            int created,
            int updated,
            int skipped,
            int failed,
            long lastMovieId
    ) {
    }
}