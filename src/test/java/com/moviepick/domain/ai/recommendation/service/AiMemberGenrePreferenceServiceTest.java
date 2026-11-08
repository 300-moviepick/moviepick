package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewStatus;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMemberGenrePreferenceServiceTest {

    private static final Long MEMBER_ID = 2L;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MovieGenreRepository movieGenreRepository;

    @InjectMocks
    private AiMemberGenrePreferenceService preferenceService;

    @Test
    void 높은_평점은_선호_장르에_양수_점수를_준다() {

        givenReviews(
                review(101L, "5.0")
        );

        // Mock 데이터 먼저 생성
        MovieGenre sfGenre = genre(101L, "SF");

        given(movieGenreRepository.findAllByMovie_IdIn(List.of(101L)))
                .willReturn(List.of(sfGenre));

        Map<String, Double> result =
                preferenceService.getGenreWeights(MEMBER_ID);

        assertThat(result)
                .containsEntry("sf", 8.0);
    }

    @Test
    void 낮은_평점은_장르에_음수_점수를_준다() {

        givenReviews(
                review(101L, "1.0")
        );

        // Mock 데이터 먼저 생성
        MovieGenre romanceGenre = genre(101L, "로맨스");

        given(movieGenreRepository.findAllByMovie_IdIn(List.of(101L)))
                .willReturn(List.of(romanceGenre));

        Map<String, Double> result =
                preferenceService.getGenreWeights(MEMBER_ID);

        assertThat(result)
                .containsEntry("로맨스", -8.0);
    }

    @Test
    void 같은_장르의_평점은_평균으로_계산한다() {

        givenReviews(
                review(101L, "5.0"),
                review(102L, "4.0")
        );

        // Mock 데이터 두 개를 미리 생성
        MovieGenre firstGenre = genre(101L, "SF");
        MovieGenre secondGenre = genre(102L, "SF");

        given(movieGenreRepository.findAllByMovie_IdIn(
                List.of(101L, 102L)
        )).willReturn(List.of(
                firstGenre,
                secondGenre
        ));

        Map<String, Double> result =
                preferenceService.getGenreWeights(MEMBER_ID);

        assertThat(result)
                .containsEntry("sf", 6.0);
    }

    @Test
    void 리뷰가_없으면_빈_취향을_반환한다() {

        givenReviews();

        Map<String, Double> result =
                preferenceService.getGenreWeights(MEMBER_ID);

        assertThat(result).isEmpty();

        verifyNoInteractions(movieGenreRepository);
    }

    @Test
    void 공개_리뷰만_조회한다() {

        givenReviews();

        preferenceService.getGenreWeights(MEMBER_ID);

        verify(reviewRepository)
                .findByMemberIdAndStatusIn(
                        eq(MEMBER_ID),
                        eq(List.of(ReviewStatus.VISIBLE)),
                        any(Pageable.class)
                );
    }

    @Test
    void 선호_장르가_있는_영화에_점수를_부여한다() {

        AiMovieCandidate candidate = movieCandidate(
                201L,
                List.of("SF", "모험")
        );

        double score = preferenceService.scoreGenrePreference(
                candidate,
                Map.of(
                        "sf", 8.0,
                        "모험", 4.0
                )
        );

        assertThat(score).isEqualTo(6.0);
    }

    @Test
    void 선호_데이터가_없으면_개인화_점수는_영점이다() {

        double score = preferenceService.scoreGenrePreference(
                movieCandidate(201L, List.of("SF")),
                Map.of()
        );

        assertThat(score).isZero();
    }

    @Test
    void 잘못된_회원_ID는_거부한다() {

        assertThatThrownBy(() ->
                preferenceService.getGenreWeights(null)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                reviewRepository,
                movieGenreRepository
        );
    }

    private void givenReviews(Review... reviews) {

        given(reviewRepository.findByMemberIdAndStatusIn(
                eq(MEMBER_ID),
                eq(List.of(ReviewStatus.VISIBLE)),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(reviews)));
    }

    private Review review(Long movieId, String rating) {

        Movie movie = mock(Movie.class);
        when(movie.getId()).thenReturn(movieId);

        Review review = mock(Review.class);
        when(review.getMovie()).thenReturn(movie);
        when(review.getRating())
                .thenReturn(new BigDecimal(rating));

        return review;
    }

    private MovieGenre genre(Long movieId, String name) {

        Movie movie = mock(Movie.class);
        when(movie.getId()).thenReturn(movieId);

        return new MovieGenre(movie, 878, name);
    }

    private AiMovieCandidate movieCandidate(
            Long id,
            List<String> genres
    ) {
        return new AiMovieCandidate(
                id,
                1000L + id,
                "테스트 영화",
                "테스트 줄거리",
                LocalDate.of(2025, 1, 1),
                genres,
                8.0,
                100.0,
                null
        );
    }

    @Test
    void 보통_평점도_장르_평균에_포함한다() {

        givenReviews(
                review(101L, "5.0"),
                review(102L, "3.0")
        );

        MovieGenre firstGenre = genre(101L, "SF");
        MovieGenre secondGenre = genre(102L, "SF");

        given(movieGenreRepository.findAllByMovie_IdIn(
                List.of(101L, 102L)
        )).willReturn(List.of(
                firstGenre,
                secondGenre
        ));

        Map<String, Double> result =
                preferenceService.getGenreWeights(MEMBER_ID);

        assertThat(result)
                .containsEntry("sf", 4.0);
    }
}