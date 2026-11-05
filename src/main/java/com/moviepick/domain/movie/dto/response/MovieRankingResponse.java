package com.moviepick.domain.movie.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovieRankingResponse(
        int rank,
        Long movieId,
        String title,
        String posterUrl,
        LocalDate releaseDate,
        BigDecimal averageRating,
        long reviewCount,
        BigDecimal rankingScore
) {
}