package com.moviepick.domain.ai.recommendation.controller;

import com.moviepick.domain.ai.recommendation.dto.AiRecommendRequest;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendResponse;
import com.moviepick.domain.ai.recommendation.service.AiRecommendationService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/recommendations")
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "ollama"
)
public class AiRecommendationController {

    private final AiRecommendationService recommendationService;

    // 로그인 회원의 AI 추천 요청
    @PostMapping
    public ResponseEntity<ApiResponse<AiRecommendResponse>> recommend(
            @LoginMember Long memberId,
            @Valid @RequestBody AiRecommendRequest request
    ) {
        AiRecommendResponse response =
                recommendationService.recommend(memberId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}