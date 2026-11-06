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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "openai"
)
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

        // ⑦ 최종 추천 후보 선정
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

        // ⑧ AI 크레딧 사용
        AiCreditSpendService.SpendResult spendResult =
                spendService.spendOne(
                        memberId,
                        requestKey
                );

        // ⑨ 동일 요청 중복 처리
        if (spendResult.duplicate()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_IN_PROGRESS
            );
        }

        List<AiRecommendedMovie> recommendedMovies;

        try {

            // ⑩ LLM 추천 이유 생성
            log.info(
                    "AI 추천 이유 생성 시작. memberId={}, requestKey={}, movies={}",
                    memberId,
                    requestKey,
                    rankedCandidates.size()
            );

            List<AiMovieReason> reasons =
                    reasonGenerator.generate(
                            request.prompt(),
                            rankedCandidates
                    );

            log.info(
                    "AI 추천 이유 생성 완료. memberId={}, requestKey={}",
                    memberId,
                    requestKey
            );

            // ⑪ 추천 결과 검증 및 응답 변환
            recommendedMovies = combineResults(
                    rankedCandidates,
                    reasons
            );

            log.info(
                    "AI 추천 결과 검증 완료. memberId={}, requestKey={}",
                    memberId,
                    requestKey
            );

        } catch (RuntimeException aiFailure) {

            // [추가] 원본 예외와 스택 트레이스 출력
            log.error(
                    "AI 추천 처리 실패. memberId={}, requestKey={}",
                    memberId,
                    requestKey,
                    aiFailure
            );

            // ⑫ 실패 시 크레딧 복구
            try {
                restoreService.restoreOne(
                        memberId,
                        requestKey
                );

                log.info(
                        "AI 크레딧 복구 처리 완료. memberId={}, requestKey={}",
                        memberId,
                        requestKey
                );

            } catch (RuntimeException restoreFailure) {

                log.error(
                        "AI 크레딧 복구 실패. memberId={}, requestKey={}",
                        memberId,
                        requestKey,
                        restoreFailure
                );

                aiFailure.addSuppressed(restoreFailure);
            }

            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_FAILED
            );
        }

        // ⑬ 정상 처리 확정
        completeService.completeOne(
                memberId,
                requestKey
        );

        log.info(
                "AI 추천 성공. memberId={}, requestKey={}, movies={}",
                memberId,
                requestKey,
                recommendedMovies.size()
        );

        // ⑭ 응답 반환
        return new AiRecommendResponse(
                request.requestKey(),
                recommendedMovies
        );
    }

    // LLM 결과의 영화 ID와 추천 후보 검증
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