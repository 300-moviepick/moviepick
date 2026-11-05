package com.moviepick.domain.review.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.review.exception.ReviewErrorCode;
import com.moviepick.global.entity.BaseEntity;
import com.moviepick.global.exception.BusinessException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate   // 수정 시 바뀐 컬럼만 UPDATE
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reviews_member_id_movie_id",
                columnNames = {"member_id", "movie_id"}),
        indexes = {
                @Index(name = "idx_reviews_movie_id_status_created_at", columnList = "movie_id, status, created_at"),
                @Index(name = "idx_reviews_member_id_status", columnList = "member_id, status"),
                // 전체 리뷰 목록 최신순
                @Index(name = "idx_reviews_status_created_at", columnList = "status, created_at")
        })
public class Review extends BaseEntity {

    private static final BigDecimal MIN_RATING = new BigDecimal("0.5");
    private static final BigDecimal MAX_RATING = new BigDecimal("5.0");
    private static final BigDecimal TWO = new BigDecimal("2");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private boolean spoiler;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewStatus status;

    // 삭제한 리뷰를 다시 쓴 시각. 처음 쓴 리뷰는 null ("다시 작성됨" 표시, A7)
    @Column(name = "rewritten_at")
    private LocalDateTime rewrittenAt;

    public static Review create(Member member, Movie movie, BigDecimal rating, String content, boolean spoiler) {
        validateRating(rating);
        Review review = new Review();
        review.member = member;
        review.movie = movie;
        review.rating = rating;
        review.content = content;
        review.spoiler = spoiler;
        review.status = ReviewStatus.VISIBLE;
        return review;
    }

    /** 작성자가 삭제한(DELETED) 리뷰를 덮어써서 VISIBLE로 되돌린다. reviewId·작성 시각은 유지, 다시 쓴 시각을 남긴다. */
    public void rewrite(BigDecimal rating, String content, boolean spoiler) {
        if (status != ReviewStatus.DELETED) {
            throw new IllegalStateException("DELETED 상태의 리뷰만 다시 작성할 수 있습니다.");
        }
        validateRating(rating);
        this.rating = rating;
        this.content = content;
        this.spoiler = spoiler;
        this.status = ReviewStatus.VISIBLE;
        this.rewrittenAt = LocalDateTime.now();
    }

    public boolean isRewritten() {
        return rewrittenAt != null;
    }

    /** 수정. 평점 차이 계산은 호출한 Service가 수정 전 평점을 보관해서 처리한다. */
    public void update(BigDecimal rating, String content, Boolean spoiler) {
        if (rating != null) {
            validateRating(rating);
            this.rating = rating;
        }
        if (content != null) {
            this.content = content;
        }
        if (spoiler != null) {
            this.spoiler = spoiler;
        }
    }

    /** 작성자 삭제 (소프트 삭제) */
    public void delete() {
        requireVisible();
        this.status = ReviewStatus.DELETED;
    }

    /** 수정·삭제 권한 확인: DELETED는 404, 남의 리뷰·BLINDED는 403 */
    public void validateModifiableBy(Long memberId) {
        if (status == ReviewStatus.DELETED) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        if (status == ReviewStatus.BLINDED || !member.getId().equals(memberId)) {
            throw new BusinessException(ReviewErrorCode.NOT_REVIEW_OWNER);
        }
    }

    public boolean isVisible() {
        return status == ReviewStatus.VISIBLE;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    private void requireVisible() {
        if (status != ReviewStatus.VISIBLE) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
    }

    /** 0.5~5.0, 0.5 단위만 허용. Service에서 먼저 호출하므로 public static */
    public static void validateRating(BigDecimal rating) {
        boolean outOfRange = rating == null
                || rating.compareTo(MIN_RATING) < 0
                || rating.compareTo(MAX_RATING) > 0;
        boolean invalidStep = !outOfRange
                && rating.multiply(TWO).remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0;
        if (outOfRange || invalidStep) {
            throw new BusinessException(ReviewErrorCode.INVALID_RATING);
        }
    }
}