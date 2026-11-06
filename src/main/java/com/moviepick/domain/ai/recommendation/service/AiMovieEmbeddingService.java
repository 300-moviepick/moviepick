package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.ai.model.embedding",
        havingValue = "ollama"
)
public class AiMovieEmbeddingService {

    private final EmbeddingModel embeddingModel;

    // 사용자 요청을 벡터로 변환
    public float[] embedRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "추천 요청 문장은 필수입니다."
            );
        }

        String text = "task: search result | query: "
                + prompt.trim();

        return embeddingModel.embed(text);
    }

    // 영화 정보를 벡터로 변환
    public float[] embedMovie(AiMovieCandidate movie) {
        return embeddingModel.embed(buildMovieText(movie));
    }

    // [추가] 임베딩과 해시 계산에 동일한 원본 텍스트 사용
    public String buildMovieText(AiMovieCandidate movie) {
        if (movie == null
                || movie.title() == null
                || movie.title().isBlank()) {
            throw new IllegalArgumentException(
                    "영화 제목은 필수입니다."
            );
        }

        String overview = movie.overview() == null
                ? ""
                : movie.overview().trim();

        String genres = movie.genres() == null
                ? ""
                : movie.genres().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));

        return "title: " + movie.title().trim()
                + " | text: 줄거리: " + overview
                + "\n장르: " + genres;
    }
}