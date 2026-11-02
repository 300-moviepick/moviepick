package com.moviepick.domain.wishlist.controller;

import com.moviepick.domain.wishlist.dto.response.WishlistMovieResponse;
import com.moviepick.domain.wishlist.dto.response.WishlistResponse;
import com.moviepick.domain.wishlist.service.WishlistService;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import com.moviepick.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    /** 영화 찜 */
    @PostMapping("/movies/{movieId}/wishlist")
    public ResponseEntity<ApiResponse<WishlistResponse>> addWishlist(
            @PathVariable Long movieId,
            @LoginMember Long memberId) {
        WishlistResponse response = wishlistService.addWishlist(memberId, movieId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /** 영화 찜 취소 */
    @DeleteMapping("/movies/{movieId}/wishlist")
    public ResponseEntity<ApiResponse<Void>> removeWishlist(
            @PathVariable Long movieId,
            @LoginMember Long memberId) {
        wishlistService.removeWishlist(memberId, movieId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 내 찜 목록 (최근 찜한 순) */
    @GetMapping("/members/me/wishlist")
    public ResponseEntity<ApiResponse<PageResponse<WishlistMovieResponse>>> getMyWishlist(
            @LoginMember Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(wishlistService.getMyWishlist(memberId, page, size)));
    }
}
