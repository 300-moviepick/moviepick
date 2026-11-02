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
        // TODO: 한국 개봉일 수집 방식 확정 후 movies.release_date_kr 값으로 채운다 (현재 항상 null)
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
                null,
                wishlist.getCreatedAt());
    }
}
