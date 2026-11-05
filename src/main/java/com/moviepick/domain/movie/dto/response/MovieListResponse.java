package com.moviepick.domain.movie.dto.response;

import com.moviepick.domain.movie.entity.Movie;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovieListResponse(
        Long movieId,
        String title,
        LocalDate releaseDate,
        // 한국 극장 개봉일 (TMDB에 KR 정보가 없으면 null)
        LocalDate releaseDateKr,
        Double voteAverage,
        String posterUrl,
        // 우리 서비스 자체 평균 평점
        BigDecimal averageRating,

        // 우리 서비스 리뷰 개수
        int reviewCount
) {
    public static MovieListResponse from(Movie movie) {
        return new MovieListResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseDate(),
                movie.getReleaseDateKr(),
                movie.getVoteAverage(),
                movie.getPosterUrl(),
                // 리뷰가 0건이면 null
                movie.getReviewCount() == 0
                        ? null
                        : movie.getAvgRating(),
                movie.getReviewCount()
        );
    }
}
