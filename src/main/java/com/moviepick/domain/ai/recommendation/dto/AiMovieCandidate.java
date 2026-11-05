package com.moviepick.domain.ai.recommendation.dto;

import com.moviepick.domain.movie.entity.Movie;

import java.time.LocalDate;
import java.util.List;

public record AiMovieCandidate(
        Long movieId,
        Long tmdbId,
        String title,
        String overview,
        LocalDate releaseDate,
        List<String> genres,
        Double voteAverage,
        Double popularity,
        String posterUrl
) {

    // Movie Entity를 추천 후보 DTO로 변환
    public static AiMovieCandidate from(
            Movie movie,
            List<String> genres
    ) {
        return new AiMovieCandidate(
                movie.getId(),
                movie.getTmdbId(),
                movie.getTitle(),
                movie.getOverview(),
                movie.getReleaseDate(),
                List.copyOf(genres),
                movie.getVoteAverage(),
                movie.getPopularity(),
                movie.getPosterUrl()
        );
    }
}
