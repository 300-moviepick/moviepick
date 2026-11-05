package com.moviepick.domain.ai.recommendation.dto;

public record AiRecommendedMovie(
        Long movieId,
        String title,
        String posterUrl,
        String reason
) {
}
