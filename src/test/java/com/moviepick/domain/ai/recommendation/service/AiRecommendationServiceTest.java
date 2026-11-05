package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.service.AiCreditCompleteService;
import com.moviepick.domain.ai.credit.service.AiCreditRestoreService;
import com.moviepick.domain.ai.credit.service.AiCreditSpendService;
import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendRequest;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendedMovie;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceTest {

    private static final Long MEMBER_ID = 2L;

    private static final UUID REQUEST_KEY = UUID.fromString(
            "550e8400-e29b-41d4-a716-446655440000"
    );

    private static final String PROMPT =
            "감동적인 SF 영화를 추천해 줘";

    @Mock
    private AiMovieCandidateService candidateService;

    // [추가] 실제 랭킹 로직을 테스트에 사용
    @Spy
    private AiMovieRankingService rankingService =
            new AiMovieRankingService();

    @Mock
    private AiMovieReasonGenerator reasonGenerator;

    @Mock
    private AiCreditSpendService spendService;

    @Mock
    private AiCreditCompleteService completeService;

    @Mock
    private AiCreditRestoreService restoreService;

    @InjectMocks
    private AiRecommendationService recommendationService;

    @Test
    void 추천에_성공하면_크레딧_사용을_확정한다() {

        List<AiMovieCandidate> candidates = candidates();

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, false
        ));

        given(reasonGenerator.generate(PROMPT, candidates))
                .willReturn(List.of(
                        new AiMovieReason(
                                1L,
                                "우주와 가족의 사랑을 다룹니다."
                        )
                ));

        var response = recommendationService.recommend(
                MEMBER_ID, request()
        );

        assertThat(response.requestKey()).isEqualTo(REQUEST_KEY);
        assertThat(response.movies()).hasSize(1);
        assertThat(response.movies().get(0).movieId()).isEqualTo(1L);

        // [변경] 후보 조회 → 랭킹 → 차감 → AI → 완료 순서
        InOrder order = inOrder(
                candidateService,
                rankingService,
                spendService,
                reasonGenerator,
                completeService
        );

        order.verify(candidateService)
                .findCandidatesForRanking();

        order.verify(rankingService)
                .rank(PROMPT, candidates, 3);

        order.verify(spendService)
                .spendOne(MEMBER_ID, REQUEST_KEY.toString());

        order.verify(reasonGenerator)
                .generate(PROMPT, candidates);

        order.verify(completeService)
                .completeOne(MEMBER_ID, REQUEST_KEY.toString());

        verifyNoInteractions(restoreService);
    }

    @Test
    void 관련성_높은_영화_3편만_AI에_전달한다() {

        List<AiMovieCandidate> pool = List.of(
                movie(
                        1L, "인기 로맨스", "연인의 사랑 이야기",
                        List.of("로맨스"), 900.0
                ),
                movie(
                        2L, "우주 탐험", "우주 탐험대의 모험",
                        List.of("SF", "모험"), 120.0
                ),
                movie(
                        3L, "별의 여정", "우주에서 벌어지는 사건",
                        List.of("SF"), 110.0
                ),
                movie(
                        4L, "미지의 행성", "우주 행성 탐사",
                        List.of("SF"), 100.0
                )
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(pool);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, false
        ));

        given(reasonGenerator.generate(
                eq(PROMPT),
                anyList()
        )).willReturn(List.of(
                new AiMovieReason(2L, "우주 탐험 이야기입니다."),
                new AiMovieReason(3L, "SF 배경의 작품입니다."),
                new AiMovieReason(4L, "행성 탐사를 다룹니다.")
        ));

        var response = recommendationService.recommend(
                MEMBER_ID, request()
        );

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L, 3L, 4L);

        // 실제 AI에 전달된 후보도 관련성 상위 3편인지 검증
        verify(reasonGenerator).generate(
                eq(PROMPT),
                argThat(selected ->
                        selected.size() == 3
                                && selected.stream()
                                .map(AiMovieCandidate::movieId)
                                .toList()
                                .equals(List.of(2L, 3L, 4L))
                )
        );

        verify(completeService)
                .completeOne(MEMBER_ID, REQUEST_KEY.toString());

        verifyNoInteractions(restoreService);
    }

    @Test
    void 추천할_영화가_없으면_크레딧을_사용하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of());

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
                )
        );

        verifyNoInteractions(
                rankingService,
                spendService,
                reasonGenerator,
                completeService,
                restoreService
        );
    }

    @Test
    void 동일한_진행_요청은_AI를_다시_호출하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, true
        ));

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_IN_PROGRESS
                )
        );

        verifyNoInteractions(
                reasonGenerator,
                completeService,
                restoreService
        );
    }

    @Test
    void AI_모델이_실패하면_크레딧을_복구한다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, false
        ));

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willThrow(
                        new IllegalStateException("Ollama 호출 실패")
                );

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_FAILED
                )
        );

        verify(restoreService)
                .restoreOne(MEMBER_ID, REQUEST_KEY.toString());

        verifyNoInteractions(completeService);
    }

    @Test
    void AI가_존재하지_않는_영화를_반환해도_복구한다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, false
        ));

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willReturn(List.of(
                        new AiMovieReason(
                                999L,
                                "존재하지 않는 영화"
                        )
                ));

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOf(BusinessException.class);

        verify(restoreService)
                .restoreOne(MEMBER_ID, REQUEST_KEY.toString());

        verifyNoInteractions(completeService);
    }

    @Test
    void COMPLETE_실패는_자동복구하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE, 2, 0, false
        ));

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willReturn(List.of(
                        new AiMovieReason(
                                1L,
                                "감동적인 SF 영화입니다."
                        )
                ));

        given(completeService.completeOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willThrow(
                new IllegalStateException("DB 확정 실패")
        );

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 확정 실패");

        verifyNoInteractions(restoreService);
    }

    private AiRecommendRequest request() {
        return new AiRecommendRequest(PROMPT, REQUEST_KEY);
    }

    private List<AiMovieCandidate> candidates() {
        return List.of(
                movie(
                        1L,
                        "인터스텔라",
                        "우주를 배경으로 가족의 사랑을 그린 영화",
                        List.of("SF", "드라마"),
                        900.0
                )
        );
    }

    private AiMovieCandidate movie(
            Long id,
            String title,
            String overview,
            List<String> genres,
            Double popularity
    ) {
        return new AiMovieCandidate(
                id,
                id + 1000,
                title,
                overview,
                LocalDate.of(2025, 1, 1),
                genres,
                8.0,
                popularity,
                null
        );
    }
}