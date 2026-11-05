package com.moviepick.domain.movie.model;

import java.time.LocalDate;

public record MovieInfo(
        String title,
        String originalTitle,
        String originalLanguage,
        String overview,
        LocalDate releaseDate,
        Integer runtimeMinutes,
        String status,
        boolean adult,
        Double voteAverage,
        Integer voteCount,
        Double popularity,
        String posterPath,
        String backdropPath,
        LocalDate releaseDateKr
) {
}
