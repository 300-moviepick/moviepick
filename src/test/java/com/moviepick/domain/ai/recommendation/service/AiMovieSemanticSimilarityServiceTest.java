package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieSemanticSimilarityServiceTest {

    private static final String MODEL = "embeddinggemma:300m";

    @Mock
    private AiMovieEmbeddingService embeddingService;

    @Mock
    private AiMovieEmbeddingRepository embeddingRepository;

    private AiMovieSemanticSimilarityService service;

    @BeforeEach
    void setUp() {
        service = new AiMovieSemanticSimilarityService(
                embeddingService,
                embeddingRepository,
                new AiCosineSimilarityService(),
                MODEL
        );
    }

    @Test
    void 의미가_같은_영화일수록_높은_점수를_반환한다() {

        String prompt = "우주 탐험 영화";

        given(embeddingRepository.findAllByModelName(MODEL))
                .willReturn(List.of(
                        stored(1L, new float[]{1f, 0f}),
                        stored(2L, new float[]{0f, 1f}),
                        stored(3L, new float[]{-1f, 0f})
                ));

        given(embeddingService.embedRequest(prompt))
                .willReturn(new float[]{1f, 0f});

        var scores = service.scoreMovies(
                prompt,
                List.of(movie(1L), movie(2L), movie(3L))
        );

        assertThat(scores.get(1L)).isEqualTo(1.0);
        assertThat(scores.get(2L)).isZero();
        assertThat(scores.get(3L)).isZero();
        assertThat(scores).hasSize(3);
    }

    @Test
    void 임베딩이_없는_영화는_0점으로_처리한다() {

        String prompt = "감동적인 영화";

        given(embeddingRepository.findAllByModelName(MODEL))
                .willReturn(List.of(
                        stored(1L, new float[]{1f, 0f})
                ));

        given(embeddingService.embedRequest(prompt))
                .willReturn(new float[]{1f, 0f});

        var scores = service.scoreMovies(
                prompt,
                List.of(movie(1L), movie(2L))
        );

        assertThat(scores.get(1L)).isEqualTo(1.0);
        assertThat(scores.get(2L)).isZero();
    }

    @Test
    void 사용자_문장은_한_번만_임베딩한다() {

        String prompt = "우주 영화";

        given(embeddingRepository.findAllByModelName(MODEL))
                .willReturn(List.of(
                        stored(1L, new float[]{1f, 0f}),
                        stored(2L, new float[]{0f, 1f})
                ));

        given(embeddingService.embedRequest(prompt))
                .willReturn(new float[]{1f, 0f});

        service.scoreMovies(
                prompt,
                List.of(movie(1L), movie(2L))
        );

        verify(embeddingService, times(1))
                .embedRequest(prompt);

        verify(embeddingRepository, times(1))
                .findAllByModelName(MODEL);
    }

    @Test
    void 후보에_해당하는_임베딩이_없으면_모델을_호출하지_않는다() {

        given(embeddingRepository.findAllByModelName(MODEL))
                .willReturn(List.of(
                        stored(99L, new float[]{1f, 0f})
                ));

        var scores = service.scoreMovies(
                "우주 영화",
                List.of(movie(1L))
        );

        assertThat(scores.get(1L)).isZero();

        verifyNoInteractions(embeddingService);
    }

    @Test
    void 벡터_차원이_다르면_해당_영화는_0점으로_처리한다() {

        String prompt = "SF 영화";

        given(embeddingRepository.findAllByModelName(MODEL))
                .willReturn(List.of(
                        stored(1L, new float[]{1f, 0f, 0f})
                ));

        given(embeddingService.embedRequest(prompt))
                .willReturn(new float[]{1f, 0f});

        var scores = service.scoreMovies(
                prompt,
                List.of(movie(1L))
        );

        assertThat(scores.get(1L)).isZero();
    }

    @Test
    void 잘못된_요청은_거부한다() {

        assertThatThrownBy(
                () -> service.scoreMovies(" ", List.of(movie(1L)))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> service.scoreMovies("SF 영화", null)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(service.scoreMovies("SF 영화", List.of()))
                .isEmpty();

        verifyNoInteractions(
                embeddingService,
                embeddingRepository
        );
    }

    private AiMovieEmbeddingRepository.StoredEmbedding stored(
            Long id,
            float[] vector
    ) {
        return new AiMovieEmbeddingRepository.StoredEmbedding(
                id,
                MODEL,
                "a".repeat(64),
                vector
        );
    }

    private AiMovieCandidate movie(Long id) {
        return new AiMovieCandidate(
                id,
                id + 1000,
                "테스트 영화 " + id,
                "테스트 줄거리",
                LocalDate.of(2025, 1, 1),
                List.of("SF"),
                8.0,
                100.0,
                null
        );
    }
}