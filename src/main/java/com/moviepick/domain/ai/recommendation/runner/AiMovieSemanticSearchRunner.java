package com.moviepick.domain.ai.recommendation.runner;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingBatchReader;
import com.moviepick.domain.ai.recommendation.service.AiMovieSemanticSimilarityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "movie.semantic-search.enabled",
        havingValue = "true"
)
public class AiMovieSemanticSearchRunner implements ApplicationRunner {

    private static final int PAGE_SIZE = 50;
    private static final int TOP_K = 5;

    private final AiMovieEmbeddingBatchReader batchReader;
    private final AiMovieSemanticSimilarityService similarityService;

    @Value("${movie.semantic-search.query:우주탐험}")
    private String query;

    @Override
    public void run(ApplicationArguments args) {

        log.info("영화 의미 검색 테스트 시작. query={}", query);

        List<AiMovieCandidate> candidates = new ArrayList<>();
        long cursor = 0L;

        // ① ID 커서 기준으로 모든 대상 영화 조회
        while (true) {
            List<AiMovieCandidate> page =
                    batchReader.findNext(cursor, PAGE_SIZE);

            if (page.isEmpty()) {
                break;
            }

            long nextCursor =
                    page.get(page.size() - 1).movieId();

            if (nextCursor <= cursor) {
                throw new IllegalStateException(
                        "영화 조회 커서가 앞으로 이동하지 않았습니다."
                );
            }

            candidates.addAll(page);
            cursor = nextCursor;
        }

        // ② 사용자 문장과 모든 영화의 의미 유사도 계산
        Map<Long, Double> scores =
                similarityService.scoreMovies(query, candidates);

        // ③ 의미 점수가 높은 순서로 TOP 5 추출
        List<AiMovieCandidate> topMovies =
                candidates.stream()
                        .filter(movie ->
                                scores.getOrDefault(
                                        movie.movieId(),
                                        0.0
                                ) > 0.0
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                        (AiMovieCandidate movie) ->
                                                scores.getOrDefault(
                                                        movie.movieId(),
                                                        0.0
                                                )
                                ).reversed()
                        )
                        .limit(TOP_K)
                        .toList();

        log.info(
                "영화 의미 검색 완료. candidates={}, results={}",
                candidates.size(),
                topMovies.size()
        );

        for (int i = 0; i < topMovies.size(); i++) {
            AiMovieCandidate movie = topMovies.get(i);

            log.info(
                    "의미 검색 TOP {}. movieId={}, title={}, similarity={}",
                    i + 1,
                    movie.movieId(),
                    movie.title(),
                    String.format(
                            java.util.Locale.ROOT,
                            "%.4f",
                            scores.get(movie.movieId())
                    )
            );
        }
    }
}