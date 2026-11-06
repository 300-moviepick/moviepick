package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiFeedbackRequest;
import com.moviepick.domain.ai.recommendation.dto.AiFeedbackResponse;
import com.moviepick.domain.ai.recommendation.entity.AiRecommendationFeedback;
import com.moviepick.domain.ai.recommendation.repository.AiRecommendationFeedbackRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiRecommendationFeedbackService {

    private final AiRecommendationFeedbackRepository feedbackRepository;
    private final MemberRepository memberRepository;
    private final MovieRepository movieRepository;

    // 피드백 등록 또는 수정
    @Transactional
    public AiFeedbackResponse upsert(
            Long memberId,
            Long movieId,
            AiFeedbackRequest request
    ) {
        validateIds(memberId, movieId);
        validateRequest(request);

        // 같은 회원의 동시 요청을 직렬화
        Member member = getMemberForUpdate(memberId);

        Movie movie = movieRepository
                .findByIdAndActiveTrue(movieId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.NOT_FOUND
                ));

        AiRecommendationFeedback feedback = feedbackRepository
                .findByMember_IdAndMovie_Id(memberId, movieId)
                .orElse(null);

        if (feedback == null) {
            // 최초 등록
            feedback = AiRecommendationFeedback.create(
                    member,
                    movie,
                    request.preference(),
                    request.reason()
            );

            feedbackRepository.save(feedback);

        } else {
            // 기존 피드백 수정 (Dirty Checking)
            feedback.change(
                    request.preference(),
                    request.reason()
            );
        }

        return new AiFeedbackResponse(
                movieId,
                feedback.getPreference(),
                feedback.getReason()
        );
    }

    // 본인 피드백만 삭제
    @Transactional
    public void delete(Long memberId, Long movieId) {
        validateIds(memberId, movieId);

        getMemberForUpdate(memberId);

        AiRecommendationFeedback feedback = feedbackRepository
                .findByMember_IdAndMovie_Id(memberId, movieId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.NOT_FOUND
                ));

        feedbackRepository.delete(feedback);
    }

    private Member getMemberForUpdate(Long memberId) {
        return memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.NOT_FOUND
                ));
    }

    private void validateIds(Long memberId, Long movieId) {
        if (memberId == null || memberId <= 0
                || movieId == null || movieId <= 0) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }

    private void validateRequest(AiFeedbackRequest request) {
        if (request == null
                || request.preference() == null
                || (request.reason() != null
                && request.reason().length() > 500)) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }
}