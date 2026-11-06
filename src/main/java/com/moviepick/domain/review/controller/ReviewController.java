package com.moviepick.domain.review.controller;

import com.moviepick.domain.review.dto.request.ReviewCreateRequest;
import com.moviepick.domain.review.dto.request.ReviewSort;
import com.moviepick.domain.review.dto.request.ReviewUpdateRequest;
import com.moviepick.domain.review.dto.response.LikeResponse;
import com.moviepick.domain.review.dto.response.ReviewCreateResponse;
import com.moviepick.domain.review.dto.response.ReviewResponse;
import com.moviepick.domain.review.dto.response.ReviewUpdateResponse;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.domain.review.service.ReviewLikeService;
import com.moviepick.domain.review.service.ReviewListCacheService;
import com.moviepick.domain.review.service.ReviewService;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import jakarta.validation.Valid;
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
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewLikeService reviewLikeService;
    private final ReviewListCacheService reviewListCacheService;

    /** 리뷰 작성 */
    @PostMapping("/movies/{movieId}/reviews")
    public ResponseEntity<ApiResponse<ReviewCreateResponse>> createReview(
            @PathVariable Long movieId,
            @LoginMember Long memberId,
            @Valid @RequestBody ReviewCreateRequest request) {
        ReviewCreateResponse response = reviewService.createReview(memberId, movieId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /** 영화별 리뷰 조회 (로그인 시 likedByMe 반영) */
    @GetMapping("/movies/{movieId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getMovieReviews(
            @PathVariable Long movieId,
            @LoginMember(required = false) Long memberId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                reviewService.getMovieReviews(movieId, memberId, ReviewSort.from(sort), page, size)));
    }

    /** 리뷰 목록 (전체) */
    @GetMapping("/movies/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewWithMovieResponse>>> getReviews(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        ReviewSort reviewSort = ReviewSort.from(sort);

        // 좋아요순은 정렬 비용이 커서 앞쪽 페이지를 캐시로 응답한다
        PageResponse<ReviewWithMovieResponse> reviews = reviewSort == ReviewSort.LIKES
                ? reviewListCacheService.getReviewsByLikes(page, size)
                : reviewService.getReviews(reviewSort, page, size);
        return ResponseEntity.ok(ApiResponse.ok(reviews));
    }

    /** 내 리뷰 조회 */
    @GetMapping("/members/me/reviews")
    public ResponseEntity<ApiResponse<PageResponse<ReviewWithMovieResponse>>> getMyReviews(
            @LoginMember Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.getMyReviews(memberId, page, size)));
    }

    /** 리뷰 수정 */
    @PatchMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewUpdateResponse>> updateReview(
            @PathVariable Long reviewId,
            @LoginMember Long memberId,
            @Valid @RequestBody ReviewUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.updateReview(memberId, reviewId, request)));
    }

    /** 리뷰 삭제 */
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long reviewId,
            @LoginMember Long memberId) {
        reviewService.deleteReview(memberId, reviewId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 리뷰 좋아요 */
    @PostMapping("/reviews/{reviewId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> likeReview(
            @PathVariable Long reviewId,
            @LoginMember Long memberId) {
        LikeResponse response = reviewLikeService.likeReview(memberId, reviewId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /** 리뷰 좋아요 취소 */
    @DeleteMapping("/reviews/{reviewId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> unlikeReview(
            @PathVariable Long reviewId,
            @LoginMember Long memberId) {
        return ResponseEntity.ok(ApiResponse.ok(reviewLikeService.unlikeReview(memberId, reviewId)));
    }
}