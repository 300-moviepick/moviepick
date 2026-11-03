package com.moviepick.domain.wishlist.dto.response;

import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.wishlist.entity.Wishlist;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WishlistMovieResponse(
        Long movieId,
        String title,
        String posterUrl,
        LocalDate releaseDate,
        // 한국 극장 개봉일 (TMDB에 KR 정보가 없으면 null)
        LocalDate releaseDateKr,
        LocalDateTime wishlistedAt
) {
    public static WishlistMovieResponse from(Wishlist wishlist) {
        Movie movie = wishlist.getMovie();
        return new WishlistMovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getPosterUrl(),
                movie.getReleaseDate(),
                movie.getReleaseDateKr(),
                wishlist.getCreatedAt());
    }
}
