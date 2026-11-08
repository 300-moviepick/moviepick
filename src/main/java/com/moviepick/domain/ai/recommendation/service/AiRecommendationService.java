package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.exception.AiErrorCode;
import com.moviepick.domain.ai.credit.service.AiCreditCompleteService;
import com.moviepick.domain.ai.credit.service.AiCreditRestoreService;
import com.moviepick.domain.ai.credit.service.AiCreditSpendService;
import com.moviepick.domain.ai.credit.service.AiCreditWalletProvisioner;
import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendRequest;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendResponse;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendedMovie;
import com.moviepick.global.exception.BusinessException;
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
    private final AiCreditWalletProvisioner walletProvisioner;

    // 외부 AI 호출(임베딩·채팅)은 크레딧 차감 뒤에만 일어난다.
    // 차감 뒤 단계에서 실패하면 크레딧을 복구한다.
    public AiRecommendResponse recommend(
            Long memberId,
            AiRecommendRequest request
    ) {

        // ① 영화 후보 조회
        List<AiMovieCandidate> candidatePool =
                candidateService.findCandidatesForRanking();

        if (candidatePool == null || candidatePool.isEmpty()) {
            throw new BusinessException(
                    AiErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
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
                    AiErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
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

        String requestKey = request.requestKey().toString();

        // ⑥ 지갑이 없는 회원은 무료 크레딧 지갑 생성 (차감 트랜잭션보다 먼저)
        walletProvisioner.ensureWallet(memberId);

        // ⑦ AI 크레딧 사용 (외부 AI 호출 전. 부족하면 AI_CREDIT_INSUFFICIENT)
        AiCreditSpendService.SpendResult spendResult =
                spendService.spendOne(
                        memberId,
                        requestKey
                );

        // ⑧ 동일 요청 중복 처리
        if (spendResult.duplicate()) {
            throw new BusinessException(
                    AiErrorCode.AI_RECOMMENDATION_IN_PROGRESS
            );
        }

        List<AiRecommendedMovie> recommendedMovies;

        try {

            // ⑨ 의미 검색 점수 결합 (임베딩 호출. 실패하면 기존 점수로 대체)
            ToDoubleFunction<AiMovieCandidate> hybridScore =
                    hybridRankingScoreService.compose(
                            request.prompt(),
                            availableCandidates,
                            personalizationScore
                    );

            // ⑩ 최종 추천 후보 선정
            List<AiMovieCandidate> rankedCandidates =
                    rankingService.rank(
                            request.prompt(),
                            availableCandidates,
                            CANDIDATE_LIMIT,
                            hybridScore
                    );

            if (rankedCandidates == null || rankedCandidates.isEmpty()) {
                throw new BusinessException(
                        AiErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
                );
            }

            // ⑪ LLM 추천 이유 생성
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

            // ⑫ 추천 결과 검증 및 응답 변환
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

            // 원본 예외와 스택 트레이스 출력
            log.error(
                    "AI 추천 처리 실패. memberId={}, requestKey={}",
                    memberId,
                    requestKey,
                    aiFailure
            );

            // ⑬ 실패 시 크레딧 복구
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

            // 후보 없음은 차감 전과 같은 오류 코드로 응답한다
            if (aiFailure instanceof BusinessException businessException
                    && businessException.getErrorCode()
                    == AiErrorCode.AI_RECOMMENDATION_NO_CANDIDATES) {
                throw businessException;
            }

            throw new BusinessException(
                    AiErrorCode.AI_RECOMMENDATION_FAILED
            );
        }

        // ⑭ 정상 처리 확정
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

        // ⑮ 응답 반환
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