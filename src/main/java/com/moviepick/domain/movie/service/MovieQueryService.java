package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieDetailResponse;
import com.moviepick.domain.movie.dto.response.MovieListResponse;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 사용자 영화 목록·상세·검색
public class MovieQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MovieRepository movieRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 영화 목록 및 제목 검색
    public Page<MovieListResponse> getMovies(
            String keyword,
            Pageable pageable
    ) {

        Page<Movie> movies;

        if (keyword == null || keyword.isBlank()) {
            movies = movieRepository.findAllByActiveTrue(pageable);
        } else {
            movies = movieRepository
                    .findByActiveTrueAndTitleContainingIgnoreCase(
                            keyword.trim(),
                            pageable
                    );
        }

        return movies.map(MovieListResponse::from);
    }

    // 최근 개봉작 목록 (오늘은 한국 시간 기준)
    public Page<MovieListResponse> getRecentlyReleasedMovies(
            String keyword,
            Pageable pageable
    ) {
        return getRecentlyReleasedMovies(keyword, pageable, LocalDate.now(ZONE));
    }

    // 테스트에서 고정 날짜로 검증할 수 있도록 오늘 날짜를 받는다
    Page<MovieListResponse> getRecentlyReleasedMovies(
            String keyword,
            Pageable pageable,
            LocalDate today
    ) {
        String title = (keyword == null || keyword.isBlank())
                ? null
                : keyword.trim();

        return movieRepository
                .findReleased(title, today, pageable)
                .map(MovieListResponse::from);
    }

    // 영화 상세 조회
    public MovieDetailResponse getMovie(Long movieId, Long myReviewId) {

        Movie movie = movieRepository
                .findByIdAndActiveTrue(movieId)
                .orElseThrow(() ->
                        new BusinessException(
                                MovieErrorCode.MOVIE_NOT_FOUND
                        )
                );

        List<MovieGenre> genres =
                movieGenreRepository.findAllByMovie_Id(movieId);

        return MovieDetailResponse.from(movie, genres, myReviewId);
    }
}
