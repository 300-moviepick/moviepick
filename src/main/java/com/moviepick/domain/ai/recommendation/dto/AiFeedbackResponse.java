package com.moviepick.domain.ai.recommendation.dto;

import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;

public record AiFeedbackResponse(
        Long movieId,
        AiFeedbackPreference preference,
        String reason
) {
}
