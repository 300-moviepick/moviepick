package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "openai"
)
public class AiMovieSemanticSimilarityService {

    private final AiMovieEmbeddingService embeddingService;
    private final AiMovieEmbeddingRepository embeddingRepository;
    private final AiCosineSimilarityService cosineSimilarityService;
    private final String modelName;

    public AiMovieSemanticSimilarityService(
            AiMovieEmbeddingService embeddingService,
            AiMovieEmbeddingRepository embeddingRepository,
            AiCosineSimilarityService cosineSimilarityService,
            @Value("${spring.ai.openai.embedding.model}") String modelName
    ) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.cosineSimilarityService = cosineSimilarityService;
        this.modelName = modelName;
    }

    // 사용자 요청과 영화 후보들의 의미 유사도 계산
    public Map<Long, Double> scoreMovies(
            String prompt,
            List<AiMovieCandidate> candidates
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "추천 요청 문장은 필수입니다."
            );
        }

        if (candidates == null) {
            throw new IllegalArgumentException(
                    "영화 후보 목록은 null일 수 없습니다."
            );
        }

        if (candidates.isEmpty()) {
            return Map.of();
        }

        // ① 모든 후보의 기본 의미 점수는 0점
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (AiMovieCandidate movie : candidates) {
            if (movie == null || movie.movieId() == null) {
                throw new IllegalArgumentException(
                        "영화 후보 ID는 필수입니다."
                );
            }

            scores.put(movie.movieId(), 0.0);
        }

        // ② 현재 모델로 생성한 임베딩만 조회
        List<AiMovieEmbeddingRepository.StoredEmbedding> stored =
                embeddingRepository.findAllByModelName(modelName);

        // ③ 현재 추천 후보에 해당하는 임베딩만 사용
        List<AiMovieEmbeddingRepository.StoredEmbedding> matched =
                new ArrayList<>();

        for (AiMovieEmbeddingRepository.StoredEmbedding item : stored) {
            if (scores.containsKey(item.movieId())) {
                matched.add(item);
            }
        }

        if (matched.isEmpty()) {
            log.info("의미 검색 대상 임베딩이 없습니다.");
            return Collections.unmodifiableMap(scores);
        }

        // ④ 사용자 문장을 한 번만 임베딩
        float[] queryVector = embeddingService.embedRequest(prompt);

        // ⑤ 모든 영화와 코사인 유사도 계산
        for (AiMovieEmbeddingRepository.StoredEmbedding movie : matched) {
            try {
                double similarity = cosineSimilarityService.calculate(
                        queryVector,
                        movie.embedding()
                );

                // 음수 유사도는 추천 보너스 0점으로 처리
                double score = Math.max(0.0, similarity);

                scores.put(movie.movieId(), score);

            } catch (IllegalArgumentException e) {
                log.warn(
                        "영화 임베딩 차원 또는 값 오류. movieId={}, reason={}",
                        movie.movieId(),
                        e.getMessage()
                );
            }
        }

        log.info(
                "영화 의미 유사도 계산 완료. candidates={}, matched={}",
                scores.size(),
                matched.size()
        );

        return Collections.unmodifiableMap(scores);
    }
}