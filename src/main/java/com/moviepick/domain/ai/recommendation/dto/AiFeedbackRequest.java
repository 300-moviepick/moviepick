package com.moviepick.domain.ai.recommendation.dto;

import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiFeedbackRequest(

        @NotNull
        AiFeedbackPreference preference,

        @Size(max = 500)
        String reason
) {
}
