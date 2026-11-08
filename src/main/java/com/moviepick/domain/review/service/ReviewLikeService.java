package com.moviepick.domain.review.service;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.service.MemberService;
import com.moviepick.domain.review.dto.response.LikeResponse;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewLike;
import com.moviepick.domain.review.entity.ReviewStatus;
import com.moviepick.domain.review.exception.ReviewErrorCode;
import com.moviepick.domain.review.repository.ReviewLikeRepository;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewLikeService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final MemberService memberService;

    @Transactional
    public LikeResponse likeReview(Long memberId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(Review::isVisible)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND));

        if (review.isOwnedBy(memberId)) {
            throw new BusinessException(ReviewErrorCode.CANNOT_LIKE_OWN_REVIEW);
        }

        Member member = memberService.getMember(memberId);

        // 좋아요 수는 review_likes 집계라서 insert 한 번이면 끝난다 (따로 올릴 카운터 없음)
        try {
            reviewLikeRepository.saveAndFlush(ReviewLike.create(review, member));
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateLike(e)) {
                throw new BusinessException(ReviewErrorCode.ALREADY_LIKED);
            }
            throw e;
        }

        return new LikeResponse(true, countLikes(reviewId));
    }

    private boolean isDuplicateLike(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.toLowerCase().contains(ReviewLike.UK_MEMBER_REVIEW);
            }
        }
        return false;
    }

    @Transactional
    public LikeResponse unlikeReview(Long memberId, Long reviewId) {
        if (!reviewRepository.existsByIdAndStatus(reviewId, ReviewStatus.VISIBLE)) {
            throw new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }

        // 지운 행이 없으면 좋아요 기록이 없는 것
        int deleted = reviewLikeRepository.deleteByReviewIdAndMemberId(reviewId, memberId);
        if (deleted == 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_LIKE_NOT_FOUND);
        }

        return new LikeResponse(false, countLikes(reviewId));
    }

    private int countLikes(Long reviewId) {
        return Math.toIntExact(reviewLikeRepository.countByReviewId(reviewId));
    }
}