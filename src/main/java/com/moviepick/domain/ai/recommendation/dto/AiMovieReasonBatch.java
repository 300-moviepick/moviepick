package com.moviepick.domain.ai.recommendation.dto;

import java.util.List;

public record AiMovieReasonBatch(
        List<AiMovieReason> reasons
) {
}