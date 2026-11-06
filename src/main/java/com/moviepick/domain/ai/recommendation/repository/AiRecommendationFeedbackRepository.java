package com.moviepick.domain.ai.recommendation.repository;

import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;
import com.moviepick.domain.ai.recommendation.entity.AiRecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiRecommendationFeedbackRepository
        extends JpaRepository<AiRecommendationFeedback, Long> {

    // 회원이 특정 영화에 남긴 피드백
    Optional<AiRecommendationFeedback> findByMember_IdAndMovie_Id(
            Long memberId,
            Long movieId
    );

    // 특정 회원의 선호·비선호 영화 조회
    List<AiRecommendationFeedback> findAllByMember_IdAndPreference(
            Long memberId,
            AiFeedbackPreference preference
    );
}