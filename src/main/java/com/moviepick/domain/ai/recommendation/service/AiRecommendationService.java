package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.credit.service.AiCreditCompleteService;
import com.moviepick.domain.ai.credit.service.AiCreditRestoreService;
import com.moviepick.domain.ai.credit.service.AiCreditSpendService;
import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendRequest;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendResponse;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendedMovie;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private static final int CANDIDATE_LIMIT = 3;

    private final AiMovieCandidateService candidateService;
    private final AiMovieRankingService rankingService;
    private final AiMemberGenrePreferenceService preferenceService;
    private final AiFeedbackRankingService feedbackRankingService;
    private final AiHybridRankingScoreService hybridRankingScoreService;
    private final AiMovieReasonGenerator reasonGenerator;
    private final AiCreditSpendService spendService;
    private final AiCreditCompleteService completeService;
    private final AiCreditRestoreService restoreService;

    public AiRecommendResponse recommend(
            Long memberId,
            AiRecommendRequest request
    ) {

        // ① 영화 후보 조회
        List<AiMovieCandidate> candidatePool =
                candidateService.findCandidatesForRanking();

        // 후보가 없으면 크레딧 차감 없이 종료
        if (candidatePool == null || candidatePool.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // ② 회원 피드백 조회
        var feedbackProfile =
                feedbackRankingService.loadProfile(memberId);

        // ③ DISLIKE 영화 제외
        List<AiMovieCandidate> availableCandidates =
                candidatePool.stream()
                        .filter(movie ->
                                !feedbackProfile.isDisliked(movie)
                        )
                        .toList();

        // DISLIKE 제외 후 후보가 없어도 차감하지 않음
        if (availableCandidates.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // ④ 회원 장르 선호도 조회
        Map<String, Double> genreWeights =
                preferenceService.getGenreWeights(memberId);

        // ⑤ 기존 개인화 점수 계산
        ToDoubleFunction<AiMovieCandidate> personalizationScore =
                movie ->
                        preferenceService.scoreGenrePreference(
                                movie,
                                genreWeights
                        ) + feedbackProfile.bonusFor(movie);

        // ⑥ 의미 검색 점수 결합
        ToDoubleFunction<AiMovieCandidate> hybridScore =
                hybridRankingScoreService.compose(
                        request.prompt(),
                        availableCandidates,
                        personalizationScore
                );

        // ⑦ 기존 랭킹 서비스로 최종 후보 선정
        List<AiMovieCandidate> rankedCandidates =
                rankingService.rank(
                        request.prompt(),
                        availableCandidates,
                        CANDIDATE_LIMIT,
                        hybridScore
                );

        if (rankedCandidates == null || rankedCandidates.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        String requestKey = request.requestKey().toString();

        // ⑧ AI 크레딧 차감
        AiCreditSpendService.SpendResult spendResult =
                spendService.spendOne(
                        memberId,
                        requestKey
                );

        // ⑨ 동일한 진행 요청이면 AI를 다시 호출하지 않음
        if (spendResult.duplicate()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_IN_PROGRESS
            );
        }

        List<AiRecommendedMovie> recommendedMovies;

        try {

            // ⑩ LLM으로 영화별 추천 이유 생성
            List<AiMovieReason> reasons =
                    reasonGenerator.generate(
                            request.prompt(),
                            rankedCandidates
                    );

            // ⑪ 영화 정보와 추천 이유 결합
            recommendedMovies = combineResults(
                    rankedCandidates,
                    reasons
            );

        } catch (RuntimeException aiFailure) {

            // ⑫ AI 처리 실패 시 크레딧 복구
            try {
                restoreService.restoreOne(
                        memberId,
                        requestKey
                );
            } catch (RuntimeException restoreFailure) {
                aiFailure.addSuppressed(restoreFailure);
            }

            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_FAILED
            );
        }

        // ⑬ 성공 처리 (COMPLETE 실패는 자동복구하지 않음)
        completeService.completeOne(
                memberId,
                requestKey
        );

        // ⑭ 최종 응답 반환
        return new AiRecommendResponse(
                request.requestKey(),
                recommendedMovies
        );
    }

    // LLM이 반환한 영화 ID와 실제 후보 영화 ID 검증
    private List<AiRecommendedMovie> combineResults(
            List<AiMovieCandidate> candidates,
            List<AiMovieReason> reasons
    ) {

        if (reasons == null
                || reasons.size() != candidates.size()) {
            throw new IllegalStateException(
                    "AI 추천 결과의 영화 수가 일치하지 않습니다."
            );
        }

        Map<Long, String> reasonMap = new HashMap<>();

        for (AiMovieReason reason : reasons) {

            if (reason == null
                    || reason.movieId() == null
                    || reason.reason() == null
                    || reason.reason().isBlank()) {
                throw new IllegalStateException(
                        "AI 추천 결과에 잘못된 영화가 포함되었습니다."
                );
            }

            boolean candidateExists =
                    candidates.stream()
                            .anyMatch(movie ->
                                    movie.movieId().equals(
                                            reason.movieId()
                                    )
                            );

            if (!candidateExists) {
                throw new IllegalStateException(
                        "AI 추천 결과에 잘못된 영화가 포함되었습니다."
                );
            }

            String previous = reasonMap.putIfAbsent(
                    reason.movieId(),
                    reason.reason()
            );

            if (previous != null) {
                throw new IllegalStateException(
                        "AI 추천 결과에 중복 영화가 있습니다."
                );
            }
        }

        return candidates.stream()
                .map(movie ->
                        new AiRecommendedMovie(
                                movie.movieId(),
                                movie.title(),
                                movie.posterUrl(),
                                reasonMap.get(movie.movieId())
                        )
                )
                .toList();
    }
}