package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.client.TmdbClient;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieCollectionServiceTest {

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private MovieSaveService movieSaveService;

    @InjectMocks
    private MovieCollectionService movieCollectionService;

    // 기존 테스트: 일부 영화 저장 실패 시 나머지 영화 계속 저장
    @Test
    @DisplayName("영화 3편 중 1편이 실패해도 나머지 2편은 저장한다")
    void collectMovies_skipFailedMovie() {

        TmdbPopularResponse popularResponse =
                new TmdbPopularResponse(
                        1,
                        List.of(
                                new TmdbPopularResponse.MovieSummary(101L, "영화 A"),
                                new TmdbPopularResponse.MovieSummary(102L, "영화 B"),
                                new TmdbPopularResponse.MovieSummary(103L, "영화 C")
                        ),
                        1,
                        3
                );

        TmdbMovieResponse movieA = createMovie(101L, "영화 A");
        TmdbMovieResponse movieB = createMovie(102L, "영화 B");
        TmdbMovieResponse movieC = createMovie(103L, "영화 C");

        given(tmdbClient.getPopularMovies(1))
                .willReturn(popularResponse);

        given(tmdbClient.getMovieDetail(101L)).willReturn(movieA);
        given(tmdbClient.getMovieDetail(102L)).willReturn(movieB);
        given(tmdbClient.getMovieDetail(103L)).willReturn(movieC);

        given(movieSaveService.saveOne(movieA)).willReturn(1L);
        given(movieSaveService.saveOne(movieB))
                .willThrow(new RuntimeException("DB 저장 실패"));
        given(movieSaveService.saveOne(movieC)).willReturn(3L);

        MovieSyncResponse result =
                movieCollectionService.collectPopularMoviesWithResult(1);

        assertThat(result.requestedPages()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);

        verify(movieSaveService).saveOne(movieC);
    }

    // 신규 1: 초기 대량 수집 100페이지 허용
    @Test
    @DisplayName("초기 대량 수집은 100페이지까지 처리한다")
    void collectInitialMovies_100Pages() {

        given(tmdbClient.getPopularMovies(anyInt()))
                .willAnswer(invocation -> {
                    Integer page = invocation.getArgument(0);

                    return new TmdbPopularResponse(
                            page,
                            List.of(
                                    new TmdbPopularResponse.MovieSummary(
                                            page.longValue(),
                                            "영화 " + page
                                    )
                            ),
                            100,
                            100
                    );
                });

        given(tmdbClient.getMovieDetail(anyLong()))
                .willAnswer(invocation -> {
                    Long id = invocation.getArgument(0);
                    return createMovie(id, "영화 " + id);
                });

        given(movieSaveService.saveOne(any(TmdbMovieResponse.class)))
                .willReturn(1L);

        MovieSyncResponse result =
                movieCollectionService
                        .collectInitialPopularMoviesWithResult(100);

        assertThat(result.requestedPages()).isEqualTo(100);
        assertThat(result.savedCount()).isEqualTo(100);
        assertThat(result.failedCount()).isZero();
        assertThat(result.stoppedPage()).isNull();

        verify(tmdbClient, times(100)).getPopularMovies(anyInt());
        verify(tmdbClient).getPopularMovies(100);
        verify(movieSaveService, times(100))
                .saveOne(any(TmdbMovieResponse.class));
    }

    // 신규 2: 초기 수집 100페이지 초과 방지
    @Test
    @DisplayName("초기 대량 수집은 101페이지 요청을 거부한다")
    void collectInitialMovies_exceedMaxPages() {

        assertThatThrownBy(() ->
                movieCollectionService
                        .collectInitialPopularMoviesWithResult(101)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(tmdbClient, movieSaveService);
    }

    // 신규 3: 기존 정기 수집 5페이지 제한 유지
    @Test
    @DisplayName("정기 수집은 6페이지 요청을 거부한다")
    void collectPopularMovies_exceedMaxPages() {

        assertThatThrownBy(() ->
                movieCollectionService
                        .collectPopularMoviesWithResult(6)
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(tmdbClient, movieSaveService);
    }

    // 테스트용 영화 상세 데이터 생성
    private TmdbMovieResponse createMovie(Long tmdbId, String title) {
        return new TmdbMovieResponse(
                tmdbId,
                title,
                title,
                "ko",
                "테스트 영화 줄거리",
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
