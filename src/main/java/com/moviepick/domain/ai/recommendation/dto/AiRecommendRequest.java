package com.moviepick.domain.ai.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiRecommendRequest(

        @NotBlank
        @Size(max = 500)
        String prompt,

        @NotNull
        UUID requestKey
) {
}
