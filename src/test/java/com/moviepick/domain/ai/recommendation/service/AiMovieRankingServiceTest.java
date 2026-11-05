package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AiMovieRankingServiceTest {

    private final AiMovieRankingService rankingService =
            new AiMovieRankingService();

    @Test
    void 장르가_일치하면_인기도보다_우선한다() {

        AiMovieCandidate popular = movie(
                1L,
                "인기 로맨스",
                "도시에서 만난 연인 이야기",
                List.of("로맨스"),
                900.0
        );

        AiMovieCandidate relevant = movie(
                2L,
                "우주 탐험",
                "우주를 여행하는 탐험대의 모험",
                List.of("SF", "모험"),
                120.0
        );

        var result = rankingService.rank(
                "SF 영화 추천해 줘",
                List.of(popular, relevant),
                2
        );

        assertThat(result)
                .extracting(AiMovieCandidate::movieId)
                .containsExactly(2L, 1L);
    }

    @Test
    void 줄거리_키워드가_일치하면_우선한다() {

        AiMovieCandidate popular = movie(
                1L,
                "학교 이야기",
                "교실에서 일어나는 평범한 사건",
                List.of("드라마"),
                900.0
        );

        AiMovieCandidate relevant = movie(
                2L,
                "별빛 여행",
                "우주를 탐험하는 사람들의 이야기",
                List.of("드라마"),
                100.0
        );

        var result = rankingService.rank(
                "우주를 배경으로 한 영화 추천해 줘",
                List.of(popular, relevant),
                2
        );

        assertThat(result.get(0).movieId()).isEqualTo(2L);
    }

    @Test
    void 로맨스와_멜로를_동일한_장르로_인식한다() {

        AiMovieCandidate action = movie(
                1L,
                "도시 추격",
                "범죄자를 추격하는 이야기",
                List.of("액션"),
                900.0
        );

        AiMovieCandidate romance = movie(
                2L,
                "첫사랑",
                "두 사람의 만남을 그린 이야기",
                List.of("로맨스"),
                100.0
        );

        var result = rankingService.rank(
                "멜로 영화 추천해 줘",
                List.of(action, romance),
                2
        );

        assertThat(result.get(0).movieId()).isEqualTo(2L);
    }

    @Test
    void 관련성_점수가_같으면_인기도순으로_정렬한다() {

        AiMovieCandidate first = movie(
                1L, "영화 A", "평범한 이야기",
                List.of("드라마"), 100.0
        );

        AiMovieCandidate second = movie(
                2L, "영화 B", "평범한 이야기",
                List.of("드라마"), 500.0
        );

        var result = rankingService.rank(
                "영화 추천해 줘",
                List.of(first, second),
                2
        );

        assertThat(result)
                .extracting(AiMovieCandidate::movieId)
                .containsExactly(2L, 1L);
    }

    @Test
    void 요청한_개수만큼_반환한다() {

        var result = rankingService.rank(
                "SF 영화 추천해 줘",
                List.of(
                        movie(1L, "A", "우주", List.of("SF"), 50.0),
                        movie(2L, "B", "우주", List.of("SF"), 40.0),
                        movie(3L, "C", "우주", List.of("SF"), 30.0)
                ),
                2
        );

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AiMovieCandidate::movieId)
                .containsExactly(1L, 2L);
    }

    @Test
    void 후보가_없으면_빈_목록을_반환한다() {

        var result = rankingService.rank(
                "SF 영화 추천해 줘",
                List.of(),
                3
        );

        assertThat(result).isEmpty();
    }

    @Test
    void 잘못된_요청은_거부한다() {

        assertThatThrownBy(() ->
                rankingService.rank(
                        " ",
                        List.of(),
                        3
                )
        ).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() ->
                rankingService.rank(
                        "SF 영화 추천",
                        List.of(),
                        21
                )
        ).isInstanceOf(BusinessException.class);
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