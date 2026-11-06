package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(
        name = "spring.ai.model.embedding",
        havingValue = "ollama"
)
public class AiMovieEmbeddingSyncService {

    private static final int MAX_PILOT_LIMIT = 100;

    private final AiMovieCandidateService candidateService;
    private final AiMovieEmbeddingService embeddingService;
    private final AiMovieEmbeddingRepository embeddingRepository;
    private final String modelName;

    public AiMovieEmbeddingSyncService(
            AiMovieCandidateService candidateService,
            AiMovieEmbeddingService embeddingService,
            AiMovieEmbeddingRepository embeddingRepository,
            @Value("${spring.ai.ollama.embedding.model}") String modelName
    ) {
        this.candidateService = candidateService;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.modelName = modelName;
    }

    // 기존 10편 시범 수집 기능 유지
    public SyncResult sync(int limit) {

        if (limit < 1 || limit > MAX_PILOT_LIMIT) {
            throw new IllegalArgumentException(
                    "시범 수집 개수는 1~100이어야 합니다."
            );
        }

        List<AiMovieCandidate> pool =
                candidateService.findCandidatesForRanking();

        int targetCount = Math.min(limit, pool.size());

        return syncMovies(pool.subList(0, targetCount));
    }

    // [추가] 외부에서 전달받은 영화 목록 임베딩
    public SyncResult syncMovies(List<AiMovieCandidate> movies) {

        if (movies == null) {
            throw new IllegalArgumentException(
                    "영화 목록은 null일 수 없습니다."
            );
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (AiMovieCandidate movie : movies) {

            Long movieId =
                    movie == null ? null : movie.movieId();

            try {
                // ① 영화 원본 텍스트 생성
                String content =
                        embeddingService.buildMovieText(movie);

                // ② SHA-256 해시
                String contentHash = sha256(content);

                // ③ 기존 임베딩 조회
                Optional<AiMovieEmbeddingRepository.StoredEmbedding>
                        existing =
                        embeddingRepository.findByMovieId(movieId);

                // ④ 동일 모델 + 동일 내용이면 생략
                if (existing.isPresent()
                        && modelName.equals(
                        existing.get().modelName()
                )
                        && contentHash.equals(
                        existing.get().contentHash()
                )) {

                    skipped++;

                    log.info(
                            "영화 임베딩 생략. movieId={}, title={}",
                            movieId,
                            movie.title()
                    );

                    continue;
                }

                // ⑤ 신규·변경 영화만 Ollama 호출
                float[] embedding =
                        embeddingService.embedMovie(movie);

                // ⑥ MySQL UPSERT
                embeddingRepository.upsert(
                        movieId,
                        modelName,
                        contentHash,
                        embedding
                );

                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }

                log.info(
                        "영화 임베딩 저장 성공. movieId={}, title={}, dimensions={}",
                        movieId,
                        movie.title(),
                        embedding.length
                );

            } catch (RuntimeException e) {
                failed++;

                log.error(
                        "영화 임베딩 저장 실패. movieId={}",
                        movieId,
                        e
                );
            }
        }

        return new SyncResult(
                movies.size(),
                created,
                updated,
                skipped,
                failed
        );
    }

    private String sha256(String text) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    text.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    e
            );
        }
    }

    public record SyncResult(
            int total,
            int created,
            int updated,
            int skipped,
            int failed
    ) {
    }
}