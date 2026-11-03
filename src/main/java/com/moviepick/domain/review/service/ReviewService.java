package com.moviepick.domain.review.service;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.domain.movie.service.MovieRatingService;
import com.moviepick.domain.movie.service.MovieReviewReader;
import com.moviepick.domain.review.dto.request.ReviewCreateRequest;
import com.moviepick.domain.review.dto.request.ReviewSort;
import com.moviepick.domain.review.dto.request.ReviewUpdateRequest;
import com.moviepick.domain.review.dto.response.ReviewCreateResponse;
import com.moviepick.domain.review.dto.response.ReviewResponse;
import com.moviepick.domain.review.dto.response.ReviewUpdateResponse;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewStatus;
import com.moviepick.domain.review.exception.ReviewErrorCode;
import com.moviepick.domain.review.repository.ReviewLikeRepository;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import com.moviepick.global.response.PageResponse;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService implements MovieReviewReader {

    // 목록에 나오는 리뷰 상태: 숨긴 리뷰도 자리를 유지한다 (내용·평점은 응답에서 null)
    private static final List<ReviewStatus> LISTED_STATUSES =
            List.of(ReviewStatus.VISIBLE, ReviewStatus.BLIND);

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberService memberService;
    private final MovieRatingService movieRatingService;
    private final MovieRepository movieRepository;


    // ===================== 작성 =====================
    @CacheEvict(cacheNames = "movieRanking", allEntries = true)
    @Transactional
    public ReviewCreateResponse createReview(Long memberId, Long movieId, ReviewCreateRequest request) {
        BigDecimal rating = normalizeRating(request.rating());
        Member member = memberService.getMember(memberId);   // [수정] getActiveMember → getMember
        Movie movie = movieRatingService.getMovie(movieId);   // 없으면 기존과 같은 예외
        boolean spoiler = request.spoilerOrDefault();

        // 영화 줄 쓰기 잠금을 먼저 잡는다. 리뷰 빈 자리 잠금보다 앞서야 같은 영화 작성이 한 줄로 서서 데드락이 안 난다
        movieRepository.findByIdForUpdate(movieId);

        // 같은 (회원, 영화)의 기존 리뷰 상태에 따라 분기
        Review review = reviewRepository.findByMemberIdAndMovieIdForUpdate(memberId, movieId)
                .map(existing -> rewriteIfDeleted(existing, rating, request.content(), spoiler))
                .orElseGet(() -> saveNew(member, movie, rating, request.content(), spoiler));

        movieRatingService.applyRating(movieId, rating, 1);
        return ReviewCreateResponse.from(review);
    }

    private Review rewriteIfDeleted(Review existing, BigDecimal rating, String content, boolean spoiler) {
        switch (existing.getStatus()) {
            case VISIBLE -> throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
            case BLIND -> throw new BusinessException(ReviewErrorCode.REVIEW_BLINDED);
            default -> {
                // [수정] 이전 좋아요 기록을 지운 뒤 덮어쓴다 (다시 쓴 리뷰는 좋아요 0부터 시작)
                reviewLikeRepository.deleteAllByReviewId(existing.getId());
                existing.rewrite(rating, content, spoiler);   // DELETED → VISIBLE
            }
        }
        return existing;
    }

    private Review saveNew(Member member, Movie movie, BigDecimal rating, String content, boolean spoiler) {
        try {
            return reviewRepository.saveAndFlush(Review.create(member, movie, rating, content, spoiler));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 유니크 제약(member_id, movie_id)에 걸린 경우
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    // ===================== 조회 =====================

    public PageResponse<ReviewResponse> getMovieReviews(
            Long movieId, Long memberId, ReviewSort sort, int page, int size) {
        movieRatingService.getMovie(movieId);   // 없으면 MOVIE_NOT_FOUND

        Page<Review> reviews = sort == ReviewSort.LIKES
                ? reviewRepository.findByMovieIdOrderByLikes(movieId, LISTED_STATUSES, PageRequest.of(page, size))
                : reviewRepository.findByMovieIdAndStatusIn(
                        movieId, LISTED_STATUSES, PageRequest.of(page, size, sort.getSort()));
        Map<Long, Integer> likeCounts = countLikes(reviews.getContent());
        Set<Long> likedIds = findLikedReviewIds(memberId, reviews.getContent());

        return PageResponse.from(reviews.map(r -> ReviewResponse.of(
                r, likeCounts.getOrDefault(r.getId(), 0), likedIds.contains(r.getId()))));
    }

    public PageResponse<ReviewWithMovieResponse> getReviews(ReviewSort sort, int page, int size) {
        // 비활성 영화의 리뷰는 전체 목록에서 뺀다 (내 리뷰에는 남김)
        Page<Review> reviews = sort == ReviewSort.LIKES
                ? reviewRepository.findAllOrderByLikes(LISTED_STATUSES, PageRequest.of(page, size))
                : reviewRepository.findByStatusInAndMovieActiveTrue(
                        LISTED_STATUSES, PageRequest.of(page, size, sort.getSort()));
        Map<Long, Integer> likeCounts = countLikes(reviews.getContent());
        return PageResponse.from(reviews.map(r ->
                ReviewWithMovieResponse.withAuthor(r, likeCounts.getOrDefault(r.getId(), 0))));
    }

    public PageResponse<ReviewWithMovieResponse> getMyReviews(Long memberId, int page, int size) {
        Page<Review> reviews = reviewRepository.findByMemberIdAndStatusIn(
                memberId, LISTED_STATUSES,
                PageRequest.of(page, size, ReviewSort.LATEST.getSort()));
        Map<Long, Integer> likeCounts = countLikes(reviews.getContent());
        return PageResponse.from(reviews.map(r ->
                ReviewWithMovieResponse.forMine(r, likeCounts.getOrDefault(r.getId(), 0))));
    }

    /**
     * 영화 상세의 myReviewId (MovieReviewReader 구현).
     * 영화 컨트롤러는 인터페이스로만 부르므로 movie 도메인은 review를 의존하지 않는다.
     */
    @Override
    public Optional<Long> findMyReviewId(Long memberId, Long movieId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return reviewRepository.findIdByMemberIdAndMovieIdAndStatus(memberId, movieId, ReviewStatus.VISIBLE);
    }

    // ===================== 수정 =====================

    @CacheEvict(cacheNames = "movieRanking", allEntries = true)
    @Transactional
    public ReviewUpdateResponse updateReview(Long memberId, Long reviewId, ReviewUpdateRequest request) {
        BigDecimal newRating = request.rating() == null ? null : normalizeRating(request.rating());

        Review review = findReviewForUpdate(reviewId);
        review.validateModifiableBy(memberId);

        // 비활성 영화의 리뷰는 수정할 수 없다 (삭제는 허용)
        if (!review.getMovie().isActive()) {
            throw new BusinessException(MovieErrorCode.MOVIE_NOT_FOUND);
        }

        BigDecimal oldRating = review.getRating();
        review.update(newRating, request.content(), request.spoiler());

        // 평점이 바뀌면 차이만큼만 합계에 반영 (리뷰 수는 그대로)
        if (newRating != null) {
            BigDecimal delta = newRating.subtract(oldRating);
            if (delta.signum() != 0) {
                movieRatingService.applyRating(review.getMovie().getId(), delta, 0);
            }
        }

        reviewRepository.flush();   // updatedAt 반영 후 응답 생성
        return ReviewUpdateResponse.from(review);
    }

    // ===================== 삭제 =====================

    @CacheEvict(cacheNames = "movieRanking", allEntries = true)
    @Transactional
    public void deleteReview(Long memberId, Long reviewId) {
        Review review = findReviewForUpdate(reviewId);
        review.validateModifiableBy(memberId);
        review.delete();   // VISIBLE → DELETED

        movieRatingService.applyRating(review.getMovie().getId(), review.getRating().negate(), -1);
    }

    /**
     * 관리자 파트(PATCH /api/admin/reviews/{reviewId}/blind)에서 호출. VISIBLE → BLIND + 평점 집계 차감을 한 트랜잭션으로 처리.
     * 상태 변경은 조건부 UPDATE라서, 이미 BLIND/DELETED면 반영 행이 0 → 차감하지 않는다 (중복 블라인드·작성자 삭제와 겹쳐도 안전).
     * 존재하지 않는 리뷰만 REVIEW_NOT_FOUND, 이미 숨겨진 리뷰는 아무것도 하지 않는다 (멱등).
     */
    @CacheEvict(cacheNames = "movieRanking", allEntries = true)
    @Transactional
    public void blind(Long reviewId) {
        int changed = reviewRepository.changeStatus(
                reviewId, ReviewStatus.VISIBLE, ReviewStatus.BLIND, LocalDateTime.now());
        if (changed == 0) {
            if (!reviewRepository.existsById(reviewId)) {
                throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
            }
            return;
        }

        // UPDATE로 이미 행 잠금을 잡았으므로, 잠금 조회로 최신 평점을 읽어 차감한다.
        Review review = findReviewForUpdate(reviewId);
        movieRatingService.applyRating(review.getMovie().getId(), review.getRating().negate(), -1);
    }

    // ===================== 내부 메서드 =====================

    private Review findReviewForUpdate(Long reviewId) {
        return reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));
    }

    /**
     * 범위·단위 검증(INVALID_RATING) 후 소수 첫째 자리로 통일
     */
    private BigDecimal normalizeRating(BigDecimal rating) {
        Review.validateRating(rating);
        return rating.setScale(1, RoundingMode.UNNECESSARY);
    }

    /** 한 페이지 리뷰의 좋아요 수 (reviewId → 수). 좋아요가 없는 리뷰는 맵에 없으므로 getOrDefault(id, 0)로 꺼낸다 */
    private Map<Long, Integer> countLikes(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = reviews.stream().map(Review::getId).toList();
        return reviewLikeRepository.countByReviewIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> Math.toIntExact((Long) row[1])));
    }

    private Set<Long> findLikedReviewIds(Long memberId, List<Review> reviews) {
        if (memberId == null || reviews.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = reviews.stream().map(Review::getId).toList();
        return new HashSet<>(reviewLikeRepository.findLikedReviewIds(memberId, ids));
    }
}