package com.moviepick.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.movie.service.MovieRatingService;
import com.moviepick.domain.review.dto.request.ReviewCreateRequest;
import com.moviepick.domain.review.dto.request.ReviewSort;
import com.moviepick.domain.review.dto.response.ReviewResponse;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.domain.review.entity.ReviewStatus;
import com.moviepick.domain.review.exception.ReviewErrorCode;
import com.moviepick.domain.review.repository.ReviewLikeRepository;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.security.RefreshTokenService;
import com.moviepick.global.config.JpaAuditingConfig;
import com.moviepick.global.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 서비스 트랜잭션이 실제로 커밋·롤백되는지 보려고 테스트 트랜잭션을 끄고 H2로 확인한다. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({JpaAuditingConfig.class, ReviewService.class, ReviewLikeService.class,
        MovieRatingService.class, MemberService.class})
class ReviewTransactionTest {

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewLikeService reviewLikeService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private ReviewLikeRepository reviewLikeRepository;
    @MockitoSpyBean
    private ReviewRepository reviewRepository;
    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private Long writerId;
    private Long likerId;
    private Long movieId;
    private Long reviewId;

    @BeforeEach
    void setUp() {
        writerId = memberRepository.save(Member.createLocal("writer@test.com", "encoded", "writer")).getId();
        likerId = memberRepository.save(Member.createLocal("liker@test.com", "encoded", "liker")).getId();
        movieId = movieRepository.save(new Movie(1L, "movie")).getId();
        reviewId = reviewService.createReview(writerId, movieId,
                new ReviewCreateRequest(new BigDecimal("4.5"), "good", false)).reviewId();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(reviewRepository);
        reviewLikeRepository.deleteAllInBatch();
        reviewRepository.deleteAllInBatch();
        movieRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("같은 회원이 두 번 좋아요하면 두 번째는 409(ALREADY_LIKED)이고 좋아요 수는 1이다")
    void duplicateLike() {
        reviewLikeService.likeReview(likerId, reviewId);

        assertThatThrownBy(() -> reviewLikeService.likeReview(likerId, reviewId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.ALREADY_LIKED);

        assertThat(reviewLikeRepository.countByReviewId(reviewId)).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요·취소 응답의 likeCount는 review_likes 집계와 같다")
    void likeCountFromReviewLikes() {
        assertThat(reviewLikeService.likeReview(likerId, reviewId).likeCount()).isEqualTo(1);
        assertThat(reviewLikeService.unlikeReview(likerId, reviewId).likeCount()).isZero();
        assertThat(reviewLikeRepository.count()).isZero();
    }

    @Test
    @DisplayName("좋아요순은 review_likes 수가 많은 리뷰가 먼저이고, 목록의 likeCount도 집계값이다")
    void sortByLikes() {
        Long otherWriterId = memberRepository.save(Member.createLocal("other@test.com", "encoded", "other")).getId();
        Long popularId = reviewService.createReview(otherWriterId, movieId,
                new ReviewCreateRequest(new BigDecimal("3.0"), "popular", false)).reviewId();
        reviewLikeService.likeReview(likerId, popularId);
        reviewLikeService.likeReview(writerId, popularId);

        var page = reviewService.getMovieReviews(movieId, likerId, ReviewSort.LIKES, 0, 20);

        assertThat(page.content()).extracting(ReviewResponse::reviewId).containsExactly(popularId, reviewId);
        assertThat(page.content()).extracting(ReviewResponse::likeCount).containsExactly(2, 0);
        assertThat(page.content()).extracting(ReviewResponse::likedByMe).containsExactly(true, false);
        assertThat(reviewService.getReviews(ReviewSort.LIKES, 0, 20).content())
                .extracting(ReviewWithMovieResponse::reviewId).containsExactly(popularId, reviewId);
    }

    @Test
    @DisplayName("블라인드를 두 번 해도 평점 집계는 한 번만 차감된다")
    void blindTwice() {
        reviewService.blind(reviewId);
        reviewService.blind(reviewId);

        Movie movie = movieRepository.findById(movieId).orElseThrow();
        assertThat(movie.getReviewCount()).isZero();
        assertThat(movie.getRatingSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getStatus()).isEqualTo(ReviewStatus.BLINDED);
    }

    @Test
    @DisplayName("작성자가 삭제한 리뷰를 블라인드해도 평점이 다시 빠지지 않는다")
    void blindAfterDelete() {
        reviewService.deleteReview(writerId, reviewId);
        reviewService.blind(reviewId);

        Movie movie = movieRepository.findById(movieId).orElseThrow();
        assertThat(movie.getReviewCount()).isZero();
        assertThat(movie.getRatingSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getStatus()).isEqualTo(ReviewStatus.DELETED);
    }

    @Test
    @DisplayName("없는 리뷰를 블라인드하면 REVIEW_NOT_FOUND")
    void blindNotFound() {
        assertThatThrownBy(() -> reviewService.blind(reviewId + 999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_NOT_FOUND);
    }
}
