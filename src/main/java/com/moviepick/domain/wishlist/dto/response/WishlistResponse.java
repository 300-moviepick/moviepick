package com.moviepick.domain.wishlist.dto.response;

import com.moviepick.domain.wishlist.entity.Wishlist;
import java.time.LocalDateTime;

public record WishlistResponse(
        Long movieId,
        LocalDateTime wishlistedAt
) {
    public static WishlistResponse from(Wishlist wishlist) {
        return new WishlistResponse(wishlist.getMovie().getId(), wishlist.getCreatedAt());
    }
}
