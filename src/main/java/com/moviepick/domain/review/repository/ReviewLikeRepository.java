package com.moviepick.domain.review.repository;

import com.moviepick.domain.review.entity.ReviewLike;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    @Modifying(flushAutomatically = true)
    @Query("delete from ReviewLike rl where rl.review.id = :reviewId and rl.member.id = :memberId")
    int deleteByReviewIdAndMemberId(@Param("reviewId") Long reviewId, @Param("memberId") Long memberId);

    // 재작성(DELETED → VISIBLE) 시 이전 좋아요 기록 전체 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from ReviewLike rl where rl.review.id = :reviewId")
    int deleteAllByReviewId(@Param("reviewId") Long reviewId);

    @Query("select rl.review.id from ReviewLike rl where rl.member.id = :memberId and rl.review.id in :reviewIds")
    List<Long> findLikedReviewIds(@Param("memberId") Long memberId,
                                  @Param("reviewIds") Collection<Long> reviewIds);

    // ===== 좋아요 수 (like_count 컬럼 대신 review_likes 집계. idx_review_likes_review_id 사용) =====

    long countByReviewId(Long reviewId);

    // 목록용: 한 페이지의 리뷰 좋아요 수를 쿼리 한 번으로. 좋아요가 없는 리뷰는 결과에 없다
    @Query("select rl.review.id, count(rl) from ReviewLike rl where rl.review.id in :reviewIds group by rl.review.id")
    List<Object[]> countByReviewIds(@Param("reviewIds") Collection<Long> reviewIds);
}