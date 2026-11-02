package com.moviepick.domain.review.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 전체 리뷰 목록(author 포함), 내 리뷰(author 제외, movieActive 포함)용.
 * 숨긴 리뷰는 blinded=true, rating·content는 null
 */
public record ReviewWithMovieResponse(
        Long reviewId,
        ReviewMovieResponse movie,
        @JsonInclude(JsonInclude.Include.NON_NULL) AuthorResponse author,
        BigDecimal rating,
        String content,
        boolean spoiler,
        int likeCount,
        LocalDateTime createdAt,
        // 내 리뷰 전용: 영화가 비활성이면 false (수정 버튼·영화 링크를 끌 때 사용)
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean movieActive,
        boolean blinded,
        // 삭제 후 다시 쓴 리뷰면 true ("다시 작성됨" 표시)
        boolean rewritten
) {
    public static ReviewWithMovieResponse withAuthor(Review review, int likeCount) {
        return of(review, likeCount, AuthorResponse.from(review.getMember()), null);
    }

    public static ReviewWithMovieResponse forMine(Review review, int likeCount) {
        return of(review, likeCount, null, review.getMovie().isActive());
    }

    private static ReviewWithMovieResponse of(Review review, int likeCount,
                                              AuthorResponse author, Boolean movieActive) {
        boolean blinded = review.getStatus() == ReviewStatus.BLIND;

        return new ReviewWithMovieResponse(
                review.getId(),
                ReviewMovieResponse.from(review.getMovie()),
                author,
                blinded ? null : review.getRating(),
                blinded ? null : review.getContent(),
                review.isSpoiler(),
                likeCount,
                review.getCreatedAt(),
                movieActive,
                blinded,
                review.isRewritten());
    }
}
