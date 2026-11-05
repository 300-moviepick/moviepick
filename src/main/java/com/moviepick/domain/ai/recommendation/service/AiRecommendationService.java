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

    // [추가 ①] 영화 관련성 점수 계산
    private final AiMovieRankingService rankingService;

    private final AiMovieReasonGenerator reasonGenerator;

    private final AiCreditSpendService spendService;
    private final AiCreditCompleteService completeService;
    private final AiCreditRestoreService restoreService;

    // 외부 AI 호출 중 DB 트랜잭션을 유지하지 않는다.
    public AiRecommendResponse recommend(
            Long memberId,
            AiRecommendRequest request
    ) {
        String requestKey = request.requestKey().toString();

        // [변경 ②] 랭킹을 위한 영화 후보 확보
        List<AiMovieCandidate> candidatePool =
                candidateService.findCandidatesForRanking();

        if (candidatePool.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // [추가 ③] 사용자 요청과 관련성 높은 상위 3편 선정
        List<AiMovieCandidate> candidates =
                rankingService.rank(
                        request.prompt(),
                        candidatePool,
                        CANDIDATE_LIMIT
                );

        if (candidates.isEmpty()) {
            throw new BusinessException(
                    CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
            );
        }

        // ④ 기존: 크레딧 1회 차감
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
            // ⑤ 기존: 랭킹된 영화에 대한 AI 추천 이유 생성
            List<AiMovieReason> reasons =
                    reasonGenerator.generate(
                            request.prompt(),
                            candidates
                    );

            movies = combineResults(candidates, reasons);

        } catch (RuntimeException aiFailure) {

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

        // ⑥ 기존: 추천 성공 확정
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