package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.model.MovieInfo;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({
        JpaAuditingConfig.class,
        AiMovieCandidateService.class
})
class AiMovieCandidateServiceTest {

    @Autowired
    private AiMovieCandidateService candidateService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieGenreRepository genreRepository;

    @Test
    void 추천_영화_후보를_인기도순으로_조회한다() {

        saveMovie(9001L, "드라마 영화", "감동적인 이야기", 50.0, false);
        saveMovie(9002L, "우주 영화", "우주 탐험 이야기", 90.0, false);

        List<AiMovieCandidate> result =
                candidateService.findCandidates(10);

        assertThat(result)
                .extracting(AiMovieCandidate::title)
                .containsExactly("우주 영화", "드라마 영화");
    }

    @Test
    void 성인_영화와_줄거리_없는_영화는_제외한다() {

        saveMovie(9003L, "정상 영화", "정상적인 줄거리", 70.0, false);
        saveMovie(9004L, "성인 영화", "성인 영화 줄거리", 99.0, true);
        saveMovie(9005L, "빈 줄거리", "  ", 98.0, false);

        List<AiMovieCandidate> result =
                candidateService.findCandidates(10);

        assertThat(result)
                .extracting(AiMovieCandidate::title)
                .containsExactly("정상 영화");
    }

    @Test
    void 영화의_장르정보를_함께_반환한다() {

        Movie movie = saveMovie(
                9006L,
                "SF 영화",
                "우주를 여행하는 이야기",
                85.0,
                false
        );

        genreRepository.saveAndFlush(
                new MovieGenre(movie, 878, "SF")
        );

        List<AiMovieCandidate> result =
                candidateService.findCandidates(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).genres()).containsExactly("SF");
        assertThat(result.get(0).tmdbId()).isEqualTo(9006L);
    }

    @Test
    void 잘못된_후보_개수는_거부한다() {

        assertThatThrownBy(() -> candidateService.findCandidates(0))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> candidateService.findCandidates(21))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 추천할_영화가_없으면_빈_목록을_반환한다() {

        List<AiMovieCandidate> result =
                candidateService.findCandidates(10);

        assertThat(result).isEmpty();
    }

    private Movie saveMovie(
            Long tmdbId,
            String title,
            String overview,
            double popularity,
            boolean adult
    ) {
        Movie movie = new Movie(tmdbId, title);

        movie.updateDetails(new MovieInfo(
                title,
                title,
                "ko",
                overview,
                LocalDate.of(2025, 1, 1),
                120,
                "Released",
                adult,
                8.0,
                100,
                popularity,
                null,
                null,
                LocalDate.of(2025, 1, 1)
        ));

        return movieRepository.saveAndFlush(movie);
    }

    @Test
    void 랭킹용_후보는_20편_이상_조회할_수_있다() {

        for (int i = 0; i < 25; i++) {
            saveMovie(
                    9200L + i,
                    "후보 영화 " + i,
                    "영화 줄거리 " + i,
                    1000.0 - i,
                    false
            );
        }

        List<AiMovieCandidate> result =
                candidateService.findCandidatesForRanking();

        assertThat(result).hasSize(25);

        assertThat(result.get(0).title())
                .isEqualTo("후보 영화 0");

        assertThat(result.get(24).title())
                .isEqualTo("후보 영화 24");
    }
}
