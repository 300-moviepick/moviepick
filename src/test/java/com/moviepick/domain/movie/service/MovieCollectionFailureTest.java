package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.client.TmdbClient;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieCollectionFailureTest {

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private MovieSaveService movieSaveService;

    @InjectMocks
    private MovieCollectionService movieCollectionService;

    @Test
    @DisplayName("3페이지 목록 조회 실패 시 이전 저장을 유지하고 중단한다")
    void stopWhenPopularApiFailsAtThirdPage() {
        TmdbMovieResponse firstMovie = createMovie(101L);
        TmdbMovieResponse secondMovie = createMovie(102L);

        given(tmdbClient.getPopularMovies(1))
                .willReturn(createPopularResponse(1, 101L));

        given(tmdbClient.getPopularMovies(2))
                .willReturn(createPopularResponse(2, 102L));

        given(tmdbClient.getPopularMovies(3))
                .willThrow(new ResourceAccessException("TMDB 연결 실패"));

        given(tmdbClient.getMovieDetail(101L))
                .willReturn(firstMovie);

        given(tmdbClient.getMovieDetail(102L))
                .willReturn(secondMovie);

        MovieSyncResponse result =
                movieCollectionService.collectInitialPopularMoviesWithResult(4);

        assertThat(result.requestedPages()).isEqualTo(4);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        assertThat(result.stoppedPage()).isEqualTo(3);
        assertThat(result.stopReason()).isNotBlank();

        verify(movieSaveService).saveOne(firstMovie);
        verify(movieSaveService).saveOne(secondMovie);
        verify(tmdbClient, never()).getPopularMovies(4);
    }

    @Test
    @DisplayName("2페이지 상세 조회 실패 시 이전 저장을 유지하고 중단한다")
    void stopWhenMovieDetailApiFailsAtSecondPage() {
        TmdbMovieResponse firstMovie = createMovie(101L);

        given(tmdbClient.getPopularMovies(1))
                .willReturn(createPopularResponse(1, 101L));

        given(tmdbClient.getPopularMovies(2))
                .willReturn(createPopularResponse(2, 102L));

        given(tmdbClient.getMovieDetail(101L))
                .willReturn(firstMovie);

        given(tmdbClient.getMovieDetail(102L))
                .willThrow(new ResourceAccessException("TMDB 응답 지연"));

        MovieSyncResponse result =
                movieCollectionService.collectInitialPopularMoviesWithResult(4);

        assertThat(result.requestedPages()).isEqualTo(4);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.stoppedPage()).isEqualTo(2);
        assertThat(result.stopReason()).isNotBlank();

        verify(movieSaveService).saveOne(firstMovie);
        verify(movieSaveService, times(1)).saveOne(any());
        verify(tmdbClient, never()).getPopularMovies(3);
    }

    private TmdbPopularResponse createPopularResponse(
            int page,
            Long tmdbId
    ) {
        return new TmdbPopularResponse(
                page,
                List.of(new TmdbPopularResponse.MovieSummary(
                        tmdbId,
                        "영화 " + tmdbId
                )),
                4,
                4
        );
    }

    private TmdbMovieResponse createMovie(Long tmdbId) {
        return new TmdbMovieResponse(
                tmdbId,
                "영화 " + tmdbId,
                "Movie " + tmdbId,
                "en",
                "테스트 영화",
                "2026-10-01",
                120,
                "Released",
                false,
                7.5,
                100,
                10.0,
                "/poster.jpg",
                "/backdrop.jpg",
                List.of()
        );
    }
}
