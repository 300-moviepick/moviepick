package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

@Slf4j
@Service
public class AiHybridRankingScoreService {

    private final ObjectProvider<AiMovieSemanticSimilarityService>
            semanticServiceProvider;

    private final double semanticWeight;

    public AiHybridRankingScoreService(
            ObjectProvider<AiMovieSemanticSimilarityService>
                    semanticServiceProvider,
            @Value("${movie.ai.semantic-weight:5.0}")
            double semanticWeight
    ) {
        if (!Double.isFinite(semanticWeight)
                || semanticWeight < 0.0) {
            throw new IllegalArgumentException(
                    "의미 검색 가중치는 0 이상의 유한한 값이어야 합니다."
            );
        }

        this.semanticServiceProvider = semanticServiceProvider;
        this.semanticWeight = semanticWeight;
    }

    // 기존 개인화 점수에 의미 검색 점수 추가
    public ToDoubleFunction<AiMovieCandidate> compose(
            String prompt,
            List<AiMovieCandidate> candidates,
            ToDoubleFunction<AiMovieCandidate> existingScore
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "추천 요청 문장은 필수입니다."
            );
        }

        Objects.requireNonNull(
                candidates,
                "영화 후보 목록은 필수입니다."
        );

        Objects.requireNonNull(
                existingScore,
                "기존 개인화 점수 함수는 필수입니다."
        );

        if (candidates.isEmpty()) {
            return existingScore;
        }

        // 임베딩 기능이 비활성화되어도 기존 추천은 유지
        AiMovieSemanticSimilarityService semanticService =
                semanticServiceProvider.getIfAvailable();

        if (semanticService == null) {
            log.info("의미 검색 비활성화. 기존 랭킹 점수 사용");
            return existingScore;
        }

        final Map<Long, Double> semanticScores;

        try {
            // 사용자 요청을 한 번만 임베딩하고 후보별 점수 조회
            semanticScores = Map.copyOf(
                    semanticService.scoreMovies(
                            prompt,
                            candidates
                    )
            );

        } catch (RuntimeException e) {
            log.warn(
                    "의미 검색 실패. 기존 랭킹으로 대체합니다.",
                    e
            );
            return existingScore;
        }

        log.info(
                "하이브리드 랭킹 활성화. candidates={}, semanticWeight={}",
                candidates.size(),
                semanticWeight
        );

        // 영화별 기존 점수 + 의미 검색 보너스
        return movie -> {
            double baseScore =
                    existingScore.applyAsDouble(movie);

            double similarity = semanticScores.getOrDefault(
                    movie.movieId(),
                    0.0
            );

            if (!Double.isFinite(similarity)) {
                return baseScore;
            }

            double normalizedSimilarity = Math.max(
                    0.0,
                    Math.min(1.0, similarity)
            );

            return baseScore
                    + normalizedSimilarity * semanticWeight;
        };
    }
}