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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "ollama"
)
public class AiRecommendationService {

    private static final int CANDIDATE_LIMIT = 3;

    private final AiMovieCandidateService candidateService;
    private final AiMovieRankingService rankingService;

    // [추가] 회원별 장르 선호도 분석
    private final AiMemberGenrePreferenceService preferenceService;

    private final AiMovieReasonGenerator reasonGenerator;

    private final AiCreditSpendService spendService;
    private final AiCreditCompleteService completeService;
    private final AiCreditRestoreService restoreService;

    // Ollama 호출 중 DB 트랜잭션을 유지하지 않는다.
    public AiRecommendResponse recommend(
            Long memberId,
            AiRecommendRequest request
    ) {
        String requestKey = request.requestKey().toString();

        // ① 영화 후보 확보
        List<AiMovieCandidate> candidatePool =
                candidateService.findCandidatesForRanking();

        if (candidatePool.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // [추가 ②] 회원 리뷰에서 장르별 선호 점수 조회
        Map<String, Double> genreWeights =
                preferenceService.getGenreWeights(memberId);

        // [변경 ③] 관련성 + 개인화 점수를 합산해 상위 3편 선정
        List<AiMovieCandidate> candidates =
                rankingService.rank(
                        request.prompt(),
                        candidatePool,
                        CANDIDATE_LIMIT,
                        movie -> preferenceService.scoreGenrePreference(
                                movie,
                                genreWeights
                        )
                );

        if (candidates.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // ④ 크레딧 차감
        var spendResult = spendService.spendOne(
                memberId,
                requestKey
        );

        if (spendResult.duplicate()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_IN_PROGRESS
            );
        }

        List<AiRecommendedMovie> movies;

        try {
            // ⑤ AI 추천 이유 생성
            List<AiMovieReason> reasons =
                    reasonGenerator.generate(
                            request.prompt(),
                            candidates
                    );

            movies = combineResults(candidates, reasons);

        } catch (RuntimeException aiFailure) {

            // ⑥ AI 실패 시 크레딧 복구
            try {
                restoreService.restoreOne(memberId, requestKey);

            } catch (RuntimeException restoreFailure) {
                log.error(
                        "AI 추천 실패 후 크레딧 복구도 실패. memberId={}, requestKey={}",
                        memberId,
                        requestKey,
                        restoreFailure
                );

                aiFailure.addSuppressed(restoreFailure);
                throw aiFailure;
            }

            log.warn(
                    "AI 추천 실패로 크레딧 복구 완료. memberId={}, requestKey={}",
                    memberId,
                    requestKey,
                    aiFailure
            );

            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_FAILED
            );
        }

        // ⑦ 추천 성공 확정
        completeService.completeOne(memberId, requestKey);

        return new AiRecommendResponse(
                request.requestKey(),
                movies
        );
    }

    private List<AiRecommendedMovie> combineResults(
            List<AiMovieCandidate> candidates,
            List<AiMovieReason> reasons
    ) {
        if (reasons == null || reasons.size() != candidates.size()) {
            throw new IllegalStateException(
                    "AI 추천 결과의 영화 수가 일치하지 않습니다."
            );
        }

        Set<Long> candidateIds = candidates.stream()
                .map(AiMovieCandidate::movieId)
                .collect(Collectors.toSet());

        Map<Long, String> reasonMap = new HashMap<>();

        for (AiMovieReason item : reasons) {
            if (item == null
                    || item.movieId() == null
                    || !candidateIds.contains(item.movieId())
                    || item.reason() == null
                    || item.reason().isBlank()) {
                throw new IllegalStateException(
                        "AI 추천 결과에 잘못된 영화가 포함되었습니다."
                );
            }

            if (reasonMap.putIfAbsent(
                    item.movieId(),
                    item.reason()
            ) != null) {
                throw new IllegalStateException(
                        "AI 추천 결과에 중복 영화가 있습니다."
                );
            }
        }

        return candidates.stream()
                .map(movie -> new AiRecommendedMovie(
                        movie.movieId(),
                        movie.title(),
                        movie.posterUrl(),
                        reasonMap.get(movie.movieId())
                ))
                .toList();
    }
}