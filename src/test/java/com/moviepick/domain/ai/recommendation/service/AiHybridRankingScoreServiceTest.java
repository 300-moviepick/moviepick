package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiHybridRankingScoreServiceTest {

    @Mock
    private ObjectProvider<AiMovieSemanticSimilarityService>
            semanticServiceProvider;

    @Mock
    private AiMovieSemanticSimilarityService semanticService;

    private AiHybridRankingScoreService service;

    @BeforeEach
    void setUp() {
        service = new AiHybridRankingScoreService(
                semanticServiceProvider,
                5.0
        );
    }

    @Test
    void 기존_점수에_의미_점수를_추가한다() {

        AiMovieCandidate movie = movie(1L);
        List<AiMovieCandidate> candidates = List.of(movie);

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        given(semanticService.scoreMovies(
                "우주 영화",
                candidates
        )).willReturn(Map.of(1L, 0.8));

        ToDoubleFunction<AiMovieCandidate> base =
                candidate -> 3.0;

        var hybrid = service.compose(
                "우주 영화",
                candidates,
                base
        );

        // 기존 3점 + 의미 점수 0.8 * 5점 = 7점
        assertThat(hybrid.applyAsDouble(movie))
                .isCloseTo(7.0, within(0.000001));

        verify(semanticService, times(1))
                .scoreMovies("우주 영화", candidates);
    }

    @Test
    void 임베딩이_없는_영화는_기존_점수를_유지한다() {

        AiMovieCandidate movie1 = movie(1L);
        AiMovieCandidate movie2 = movie(2L);

        List<AiMovieCandidate> candidates =
                List.of(movie1, movie2);

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        given(semanticService.scoreMovies(
                "감동적인 영화",
                candidates
        )).willReturn(Map.of(1L, 0.6));

        var hybrid = service.compose(
                "감동적인 영화",
                candidates,
                candidate -> 2.0
        );

        assertThat(hybrid.applyAsDouble(movie1))
                .isCloseTo(5.0, within(0.000001));

        assertThat(hybrid.applyAsDouble(movie2))
                .isCloseTo(2.0, within(0.000001));
    }

    @Test
    void 의미_검색이_비활성화되면_기존_점수를_사용한다() {

        AiMovieCandidate movie = movie(1L);

        ToDoubleFunction<AiMovieCandidate> base =
                candidate -> 4.0;

        // Provider가 null을 반환하면 기능 비활성화
        var hybrid = service.compose(
                "SF 영화",
                List.of(movie),
                base
        );

        assertThat(hybrid.applyAsDouble(movie))
                .isEqualTo(4.0);

        verifyNoInteractions(semanticService);
    }

    @Test
    void 의미_검색_실패시_기존_추천을_유지한다() {

        AiMovieCandidate movie = movie(1L);
        List<AiMovieCandidate> candidates = List.of(movie);

        given(semanticServiceProvider.getIfAvailable())
                .willReturn(semanticService);

        given(semanticService.scoreMovies(
                "우주 영화",
                candidates
        )).willThrow(
                new IllegalStateException("Ollama 연결 실패")
        );

        var hybrid = service.compose(
                "우주 영화",
                candidates,
                candidate -> 6.0
        );

        assertThat(hybrid.applyAsDouble(movie))
                .isEqualTo(6.0);
    }

    @Test
    void 영화_후보가_없으면_의미_검색을_호출하지_않는다() {

        var hybrid = service.compose(
                "SF 영화",
                List.of(),
                candidate -> 1.0
        );

        assertThat(hybrid).isNotNull();

        verifyNoInteractions(
                semanticServiceProvider,
                semanticService
        );
    }

    @Test
    void 잘못된_가중치와_입력을_거부한다() {

        assertThatThrownBy(
                () -> new AiHybridRankingScoreService(
                        semanticServiceProvider,
                        -1.0
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> service.compose(
                        " ",
                        List.of(movie(1L)),
                        candidate -> 1.0
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(
                () -> service.compose(
                        "SF 영화",
                        null,
                        candidate -> 1.0
                )
        ).isInstanceOf(NullPointerException.class);
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