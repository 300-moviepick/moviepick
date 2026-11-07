package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.credit.entity.AiCreditBucket;
import com.moviepick.domain.ai.credit.service.AiCreditCompleteService;
import com.moviepick.domain.ai.credit.service.AiCreditRestoreService;
import com.moviepick.domain.ai.credit.service.AiCreditSpendService;
import com.moviepick.domain.ai.credit.service.AiCreditWalletProvisioner;
import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendRequest;
import com.moviepick.domain.ai.recommendation.dto.AiRecommendedMovie;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

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

    @Spy
    private AiMovieRankingService rankingService =
            new AiMovieRankingService();

    @Mock
    private AiMemberGenrePreferenceService preferenceService;

    @Mock
    private AiFeedbackRankingService feedbackRankingService;

    // [추가] 하이브리드 랭킹 Mock
    @Mock
    private AiHybridRankingScoreService hybridRankingScoreService;

    @Mock
    private AiMovieReasonGenerator reasonGenerator;

    @Mock
    private AiCreditSpendService spendService;

    @Mock
    private AiCreditCompleteService completeService;

    @Mock
    private AiCreditRestoreService restoreService;

    @Mock
    private AiCreditWalletProvisioner walletProvisioner;

    @InjectMocks
    private AiRecommendationService recommendationService;

    // [추가] 기존 개인화 점수 함수를 그대로 반환
    @BeforeEach
    void setUpHybridRanking() {
        lenient().when(hybridRankingScoreService.compose(
                anyString(),
                anyList(),
                any(ToDoubleFunction.class)
        )).thenAnswer(invocation -> invocation.getArgument(2));
    }

    @Test
    void 추천에_성공하면_크레딧_사용을_확정한다() {

        List<AiMovieCandidate> candidates = candidates();

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(PROMPT, candidates))
                .willReturn(List.of(
                        new AiMovieReason(
                                1L,
                                "우주와 가족의 사랑을 다룹니다."
                        )
                ));

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        assertThat(response.requestKey()).isEqualTo(REQUEST_KEY);
        assertThat(response.movies()).hasSize(1);
        assertThat(response.movies().get(0).movieId()).isEqualTo(1L);

        // 크레딧 차감이 의미 검색(임베딩)·랭킹·LLM 호출보다 먼저다
        InOrder order = inOrder(
                candidateService,
                feedbackRankingService,
                preferenceService,
                walletProvisioner,
                spendService,
                hybridRankingScoreService,
                rankingService,
                reasonGenerator,
                completeService
        );

        order.verify(candidateService)
                .findCandidatesForRanking();

        order.verify(feedbackRankingService)
                .loadProfile(MEMBER_ID);

        order.verify(preferenceService)
                .getGenreWeights(MEMBER_ID);

        order.verify(walletProvisioner)
                .ensureWallet(MEMBER_ID);

        order.verify(spendService)
                .spendOne(MEMBER_ID, REQUEST_KEY.toString());

        order.verify(hybridRankingScoreService)
                .compose(eq(PROMPT), eq(candidates), any());

        order.verify(rankingService)
                .rank(eq(PROMPT), eq(candidates), eq(3), any());

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

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(
                eq(PROMPT),
                anyList()
        )).willReturn(List.of(
                new AiMovieReason(2L, "우주 탐험 이야기입니다."),
                new AiMovieReason(3L, "SF 배경의 작품입니다."),
                new AiMovieReason(4L, "행성 탐사를 다룹니다.")
        ));

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L, 3L, 4L);

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
                feedbackRankingService,
                preferenceService,
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

        givenEmptyProfile(MEMBER_ID);

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

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willThrow(new IllegalStateException("Ollama 호출 실패"));

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

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willReturn(List.of(
                        new AiMovieReason(999L, "존재하지 않는 영화")
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

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(PROMPT, candidates()))
                .willReturn(List.of(
                        new AiMovieReason(1L, "감동적인 SF 영화입니다.")
                ));

        given(completeService.completeOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willThrow(new IllegalStateException("DB 확정 실패"));

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 확정 실패");

        verifyNoInteractions(restoreService);
    }

    @Test
    void 같은_요청이라도_회원_취향에_따라_순위가_달라진다() {

        String prompt = "영화 추천해 줘";

        List<AiMovieCandidate> pool = List.of(
                movie(
                        1L, "인기 로맨스", "두 연인의 이야기",
                        List.of("로맨스"), 900.0
                ),
                movie(
                        2L, "우주 모험", "우주 탐험대의 여행",
                        List.of("SF"), 100.0
                )
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(pool);

        givenEmptyProfile(2L);
        givenEmptyProfile(3L);

        given(preferenceService.getGenreWeights(2L))
                .willReturn(Map.of("sf", 8.0, "로맨스", -8.0));

        given(preferenceService.getGenreWeights(3L))
                .willReturn(Map.of("sf", -8.0, "로맨스", 8.0));

        given(preferenceService.scoreGenrePreference(
                any(AiMovieCandidate.class),
                anyMap()
        )).willAnswer(invocation -> {

            AiMovieCandidate movie = invocation.getArgument(0);
            Map<String, Double> weights = invocation.getArgument(1);

            return movie.genres().stream()
                    .map(genre -> genre.toLowerCase(Locale.ROOT))
                    .mapToDouble(genre ->
                            weights.getOrDefault(genre, 0.0)
                    )
                    .average()
                    .orElse(0.0);
        });

        given(spendService.spendOne(
                anyLong(),
                eq(REQUEST_KEY.toString())
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(
                eq(prompt),
                anyList()
        )).willAnswer(invocation -> {

            List<AiMovieCandidate> selected =
                    invocation.getArgument(1);

            return selected.stream()
                    .map(movie -> new AiMovieReason(
                            movie.movieId(),
                            movie.title() + " 추천 이유"
                    ))
                    .toList();
        });

        AiRecommendRequest request =
                new AiRecommendRequest(prompt, REQUEST_KEY);

        var memberA = recommendationService.recommend(2L, request);
        var memberB = recommendationService.recommend(3L, request);

        assertThat(memberA.movies().get(0).movieId())
                .isEqualTo(2L);

        assertThat(memberB.movies().get(0).movieId())
                .isEqualTo(1L);

        verify(completeService)
                .completeOne(2L, REQUEST_KEY.toString());

        verify(completeService)
                .completeOne(3L, REQUEST_KEY.toString());
    }

    @Test
    void DISLIKE_영화는_AI_추천_후보에서_제외한다() {

        List<AiMovieCandidate> pool = List.of(
                movie(
                        1L, "비선호 영화", "우주 탐험",
                        List.of("SF"), 900.0
                ),
                movie(
                        2L, "우주 영화", "우주를 탐험하는 이야기",
                        List.of("SF"), 100.0
                )
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(pool);

        given(feedbackRankingService.loadProfile(MEMBER_ID))
                .willReturn(new AiFeedbackRankingService.FeedbackProfile(
                        Set.of(1L),
                        Set.of()
                ));

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(eq(PROMPT), anyList()))
                .willReturn(List.of(
                        new AiMovieReason(2L, "SF 모험 영화입니다.")
                ));

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L);

        verify(reasonGenerator).generate(
                eq(PROMPT),
                argThat(selected ->
                        selected.size() == 1
                                && selected.get(0).movieId().equals(2L)
                )
        );
    }

    @Test
    void 모든_후보가_DISLIKE면_크레딧을_사용하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        given(feedbackRankingService.loadProfile(MEMBER_ID))
                .willReturn(new AiFeedbackRankingService.FeedbackProfile(
                        Set.of(1L),
                        Set.of()
                ));

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
                )
        );

        verifyNoInteractions(
                preferenceService,
                rankingService,
                spendService,
                reasonGenerator,
                completeService,
                restoreService
        );
    }

    @Test
    void LIKE_장르_가점으로_추천_순서가_변경된다() {

        String prompt = "영화 추천해 줘";

        List<AiMovieCandidate> pool = List.of(
                movie(
                        1L, "인기 로맨스", "연애 이야기",
                        List.of("로맨스"), 900.0
                ),
                movie(
                        2L, "우주 모험", "우주 탐험",
                        List.of("SF"), 100.0
                )
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(pool);

        given(feedbackRankingService.loadProfile(MEMBER_ID))
                .willReturn(new AiFeedbackRankingService.FeedbackProfile(
                        Set.of(),
                        Set.of("sf")
                ));

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        given(reasonGenerator.generate(eq(prompt), anyList()))
                .willAnswer(invocation -> {

                    List<AiMovieCandidate> selected =
                            invocation.getArgument(1);

                    return selected.stream()
                            .map(movie -> new AiMovieReason(
                                    movie.movieId(),
                                    "추천 이유"
                            ))
                            .toList();
                });

        var response = recommendationService.recommend(
                MEMBER_ID,
                new AiRecommendRequest(prompt, REQUEST_KEY)
        );

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L, 1L);

        verify(completeService)
                .completeOne(MEMBER_ID, REQUEST_KEY.toString());
    }

    @Test
    void 크레딧이_부족하면_임베딩과_채팅을_호출하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates());

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willThrow(new BusinessException(
                CommonErrorCode.AI_CREDIT_INSUFFICIENT
        ));

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_CREDIT_INSUFFICIENT
                )
        );

        verify(walletProvisioner).ensureWallet(MEMBER_ID);

        // 의미 검색(임베딩 호출)과 LLM 호출이 한 번도 없어야 한다
        verify(hybridRankingScoreService, never())
                .compose(anyString(), anyList(), any());
        verify(reasonGenerator, never())
                .generate(anyString(), anyList());

        verifyNoInteractions(completeService, restoreService);
    }

    @Test
    void 차감_후_랭킹_결과가_없으면_복구하고_후보_없음으로_응답한다() {

        List<AiMovieCandidate> candidates = candidates();

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        givenEmptyProfile(MEMBER_ID);

        given(spendService.spendOne(
                MEMBER_ID, REQUEST_KEY.toString()
        )).willReturn(successfulSpend());

        doReturn(List.of())
                .when(rankingService)
                .rank(anyString(), anyList(), anyInt(), any());

        assertThatThrownBy(() ->
                recommendationService.recommend(MEMBER_ID, request())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
                )
        );

        // 차감 뒤 실패이므로 USE + RESTORE가 남는다
        verify(restoreService)
                .restoreOne(MEMBER_ID, REQUEST_KEY.toString());

        verify(reasonGenerator, never())
                .generate(anyString(), anyList());

        verifyNoInteractions(completeService);
    }

    private void givenEmptyProfile(Long memberId) {
        given(feedbackRankingService.loadProfile(memberId))
                .willReturn(new AiFeedbackRankingService.FeedbackProfile(
                        Set.of(),
                        Set.of()
                ));
    }

    private AiCreditSpendService.SpendResult successfulSpend() {
        return new AiCreditSpendService.SpendResult(
                AiCreditBucket.FREE,
                2,
                0,
                false
        );
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