package com.moviepick.domain.review.repository;

import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewStatus;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // ===== 쓰기용: 집계가 어긋나지 않도록 행 잠금 =====

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdForUpdate(@Param("id") Long id);

    // 리뷰 ID로 연결된 영화 ID 조회 (락 획득 전 사용)
    @Query("select r.movie.id from Review r where r.id = :reviewId")
    Optional<Long> findMovieIdByReviewId(
            @Param("reviewId") Long reviewId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.member.id = :memberId and r.movie.id = :movieId")
    Optional<Review> findByMemberIdAndMovieIdForUpdate(@Param("memberId") Long memberId,
                                                       @Param("movieId") Long movieId);

    // 상태 전이 (조건부 UPDATE). 반영된 행이 1일 때만 평점 집계를 건드린다 → 중복 처리돼도 두 번 빠지지 않음
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Review r set r.status = :to, r.updatedAt = CURRENT_TIMESTAMP "
            + "where r.id = :id and r.status = :from")
    int changeStatus(@Param("id") Long id,
                     @Param("from") ReviewStatus from,
                     @Param("to") ReviewStatus to);

    // ===== 조회용 (N+1 방지: EntityGraph) =====

    @EntityGraph(attributePaths = "member")
    Page<Review> findByMovieIdAndStatusIn(Long movieId, Collection<ReviewStatus> statuses, Pageable pageable);

    // 전체 리뷰 목록용: 활성 영화의 리뷰만
    @EntityGraph(attributePaths = {"member", "movie"})
    Page<Review> findByStatusInAndMovieActiveTrue(Collection<ReviewStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "movie")
    Page<Review> findByMemberIdAndStatusIn(Long memberId, Collection<ReviewStatus> statuses, Pageable pageable);

    // ===== 좋아요순 (review_likes 집계로 정렬. Pageable에는 정렬을 넣지 않는다) =====

    @EntityGraph(attributePaths = "member")
    @Query(value = "select r from Review r "
            + "where r.movie.id = :movieId and r.status in :statuses "
            + "order by (select count(rl) from ReviewLike rl where rl.review = r) desc, r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Review r where r.movie.id = :movieId and r.status in :statuses")
    Page<Review> findByMovieIdOrderByLikes(@Param("movieId") Long movieId,
                                           @Param("statuses") Collection<ReviewStatus> statuses,
                                           Pageable pageable);

    @EntityGraph(attributePaths = {"member", "movie"})
    @Query(value = "select r from Review r "
            + "where r.status in :statuses and r.movie.active = true "
            + "order by (select count(rl) from ReviewLike rl where rl.review = r) desc, r.createdAt desc, r.id desc",
            countQuery = "select count(r) from Review r where r.status in :statuses and r.movie.active = true")
    Page<Review> findAllOrderByLikes(@Param("statuses") Collection<ReviewStatus> statuses, Pageable pageable);

    boolean existsByIdAndStatus(Long id, ReviewStatus status);

    // [수정] 추가: 영화 상세의 myReviewId 조회용 (VISIBLE 리뷰만)
    @Query("select r.id from Review r "
            + "where r.member.id = :memberId and r.movie.id = :movieId and r.status = :status")
    Optional<Long> findIdByMemberIdAndMovieIdAndStatus(@Param("memberId") Long memberId,
                                                       @Param("movieId") Long movieId,
                                                       @Param("status") ReviewStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Review r set r.status = :to, r.updatedAt = :now "
            + "where r.id = :id and r.status = :from")
    int changeStatus(@Param("id") Long id,
                     @Param("from") ReviewStatus from,
                     @Param("to") ReviewStatus to,
                     @Param("now") LocalDateTime now);
}