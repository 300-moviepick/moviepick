package com.moviepick.domain.ai.recommendation.controller;

import com.moviepick.domain.ai.recommendation.dto.AiFeedbackRequest;
import com.moviepick.domain.ai.recommendation.dto.AiFeedbackResponse;
import com.moviepick.domain.ai.recommendation.service.AiRecommendationFeedbackService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/feedback")
public class AiRecommendationFeedbackController {

    private final AiRecommendationFeedbackService feedbackService;

    // 피드백 등록·수정
    @PutMapping("/{movieId}")
    public ResponseEntity<ApiResponse<AiFeedbackResponse>> upsert(
            @LoginMember Long memberId,
            @PathVariable Long movieId,
            @Valid @RequestBody AiFeedbackRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        feedbackService.upsert(
                                memberId,
                                movieId,
                                request
                        )
                )
        );
    }

    // 피드백 삭제
    @DeleteMapping("/{movieId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @LoginMember Long memberId,
            @PathVariable Long movieId
    ) {
        feedbackService.delete(memberId, movieId);

        return ResponseEntity.ok(ApiResponse.ok());
    }
}