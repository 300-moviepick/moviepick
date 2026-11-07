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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiHybridRecommendationFlowTest {

    private static final Long MEMBER_ID = 2L;

    private static final String PROMPT =
            "감동적인 우주 영화 추천해 줘";

    private static final UUID REQUEST_KEY =
            UUID.fromString(
                    "550e8400-e29b-41d4-a716-446655440000"
            );

    @Mock
    private AiMovieCandidateService candidateService;

    @Mock
    private AiMemberGenrePreferenceService preferenceService;

    @Mock
    private AiFeedbackRankingService feedbackRankingService;

    @Mock
    private AiMovieSemanticSimilarityService semanticService;

    @Mock
    private ObjectProvider<AiMovieSemanticSimilarityService>
            semanticServiceProvider;

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

    private AiRecommendationService recommendationService;

    @BeforeEach
    void setUp() {

        // 실제 하이브리드 점수 서비스 사용
        AiHybridRankingScoreService hybridScoreService =
                new AiHybridRankingScoreService(
                        semanticServiceProvider,
                        5.0
                );

        // 실제 랭킹 서비스 사용
        AiMovieRankingService rankingService =
                new AiMovieRankingService();

        recommendationService = new AiRecommendationService(
                candidateService,
                rankingService,
                preferenceService,
                feedbackRankingService,
                hybridScoreService,
                reasonGenerator,
                spendService,
                completeService,
                restoreService,
                walletProvisioner
        );
    }

    @Test
    void 의미_유사도가_높은_영화가_우선_추천된다() {

        List<AiMovieCandidate> candidates = List.of(
                movie(1L),
                movie(2L)
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        givenEmptyProfile();

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        // 영화 2번의 의미 유사도가 더 높음
        given(semanticService.scoreMovies(
                PROMPT,
                candidates
        )).willReturn(Map.of(
                1L, 0.0,
                2L, 1.0
        ));

        givenSuccessfulSpend();
        givenReasons();

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        // 의미 점수 때문에 2번 영화가 먼저 추천됨
        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L, 1L);

        verify(semanticService, times(1))
                .scoreMovies(PROMPT, candidates);

        verify(completeService)
                .completeOne(
                        MEMBER_ID,
                        REQUEST_KEY.toString()
                );

        verifyNoInteractions(restoreService);
    }

    @Test
    void 의미_검색_장애에도_기존_추천이_진행된다() {

        List<AiMovieCandidate> candidates = List.of(
                movie(1L),
                movie(2L)
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        givenEmptyProfile();

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        // Ollama 또는 의미 검색 장애 상황
        given(semanticService.scoreMovies(
                PROMPT,
                candidates
        )).willThrow(
                new IllegalStateException("Ollama 연결 실패")
        );

        givenSuccessfulSpend();
        givenReasons();

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        // 의미 검색이 실패해도 기존 랭킹으로 추천
        assertThat(response.movies())
                .hasSize(2);

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactlyInAnyOrder(1L, 2L);

        verify(completeService)
                .completeOne(
                        MEMBER_ID,
                        REQUEST_KEY.toString()
                );

        // 추천 자체는 성공했으므로 복구하지 않음
        verifyNoInteractions(restoreService);
    }

    @Test
    void DISLIKE_영화는_의미_검색_대상에서도_제외된다() {

        List<AiMovieCandidate> candidates = List.of(
                movie(1L),
                movie(2L)
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        // 1번 영화 DISLIKE
        given(feedbackRankingService.loadProfile(MEMBER_ID))
                .willReturn(
                        new AiFeedbackRankingService.FeedbackProfile(
                                Set.of(1L),
                                Set.of()
                        )
                );

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        // 실제 의미 검색에는 2번 영화만 전달
        given(semanticService.scoreMovies(
                eq(PROMPT),
                anyList()
        )).willReturn(Map.of(
                2L, 0.9
        ));

        givenSuccessfulSpend();
        givenReasons();

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        assertThat(response.movies())
                .extracting(AiRecommendedMovie::movieId)
                .containsExactly(2L);

        verify(semanticService).scoreMovies(
                eq(PROMPT),
                argThat(selected ->
                        selected.size() == 1
                                && selected.get(0)
                                .movieId()
                                .equals(2L)
                )
        );

        verify(completeService)
                .completeOne(
                        MEMBER_ID,
                        REQUEST_KEY.toString()
                );
    }

    @Test
    void 의미_검색_기능이_없어도_기존_추천이_동작한다() {

        List<AiMovieCandidate> candidates = List.of(
                movie(1L),
                movie(2L)
        );

        given(candidateService.findCandidatesForRanking())
                .willReturn(candidates);

        givenEmptyProfile();

        // Provider 기본 반환값 null: 의미 검색 비활성
        givenSuccessfulSpend();
        givenReasons();

        var response = recommendationService.recommend(
                MEMBER_ID,
                request()
        );

        assertThat(response.movies())
                .hasSize(2);

        verify(semanticServiceProvider)
                .getIfAvailable();

        verifyNoInteractions(semanticService);

        verify(completeService)
                .completeOne(
                        MEMBER_ID,
                        REQUEST_KEY.toString()
                );
    }

    @Test
    void 후보가_없으면_의미_검색도_크레딧도_사용하지_않는다() {

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of());

        assertThatThrownBy(() ->
                recommendationService.recommend(
                        MEMBER_ID,
                        request()
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(
                        CommonErrorCode.AI_RECOMMENDATION_NO_CANDIDATES
                )
        );

        verifyNoInteractions(
                semanticServiceProvider,
                semanticService,
                preferenceService,
                feedbackRankingService,
                spendService,
                reasonGenerator,
                completeService,
                restoreService
        );
    }

    private void givenEmptyProfile() {

        given(feedbackRankingService.loadProfile(MEMBER_ID))
                .willReturn(
                        new AiFeedbackRankingService.FeedbackProfile(
                                Set.of(),
                                Set.of()
                        )
                );
    }

    private void givenSuccessfulSpend() {

        given(spendService.spendOne(
                MEMBER_ID,
                REQUEST_KEY.toString()
        )).willReturn(
                new AiCreditSpendService.SpendResult(
                        AiCreditBucket.FREE,
                        2,
                        0,
                        false
                )
        );
    }

    private void givenReasons() {

        given(reasonGenerator.generate(
                eq(PROMPT),
                anyList()
        )).willAnswer(invocation -> {

            List<AiMovieCandidate> selected =
                    invocation.getArgument(1);

            return selected.stream()
                    .map(movie -> new AiMovieReason(
                            movie.movieId(),
                            "추천 이유"
                    ))
                    .toList();
        });
    }

    private AiRecommendRequest request() {
        return new AiRecommendRequest(
                PROMPT,
                REQUEST_KEY
        );
    }

    // 영화 내용은 같고 ID만 다르게 생성
    private AiMovieCandidate movie(Long id) {

        return new AiMovieCandidate(
                id,
                id + 1000,
                "테스트 영화",
                "우주에서 벌어지는 감동적인 이야기",
                LocalDate.of(2025, 1, 1),
                List.of("SF", "드라마"),
                8.0,
                100.0,
                null
        );
    }
}