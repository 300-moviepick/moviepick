package com.moviepick.domain.ai.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record AiRecommendResponse(
        UUID requestKey,
        List<AiRecommendedMovie> movies
) {
}
