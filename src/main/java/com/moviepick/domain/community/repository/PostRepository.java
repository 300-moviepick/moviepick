package com.moviepick.domain.community.repository;

import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 전체 게시글 인기순 조회: 게시글과 좋아요 수를 함께 조회
    @Query(
            value = """
                    SELECT p, COUNT(pl.id)
                    FROM Post p
                    LEFT JOIN PostLike pl ON pl.post = p
                    WHERE p.status = :status
                    GROUP BY p
                    ORDER BY COUNT(pl.id) DESC, p.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.status = :status
                    """
    )
    Page<Object[]> findPopularPostsWithLikeCount(
            @Param("status") PostStatus status,
            Pageable pageable
    );

    // 검색 결과 인기순 조회: 게시글과 좋아요 수를 함께 조회
    @Query(
            value = """
                    SELECT p, COUNT(pl.id)
                    FROM Post p
                    LEFT JOIN PostLike pl ON pl.post = p
                    WHERE p.status = :status
                      AND (
                          LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                          OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                      )
                    GROUP BY p
                    ORDER BY COUNT(pl.id) DESC, p.createdAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.status = :status
                      AND (
                          LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                          OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                      )
                    """
    )
    Page<Object[]> searchPopularPostsWithLikeCount(
            @Param("status") PostStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 최신순 목록: 게시글만 페이지로 조회 (좋아요 수는 PostLikeRepository.countByPostIds로 따로)
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    // 최신순 검색: 제목 또는 본문 (좋아요 수는 따로 조회)
    @Query(
            value = """
                    SELECT p
                    FROM Post p
                    WHERE p.status = :status
                      AND (
                          LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                          OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                      )
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Post p
                    WHERE p.status = :status
                      AND (
                          LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                          OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                      )
                    """
    )
    Page<Post> searchByStatus(
            @Param("status") PostStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 삭제되지 않은 게시글 상세 조회
    Optional<Post> findByIdAndStatus(
            Long id,
            PostStatus status
    );

    // 조회수 원자적 증가
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Post p
            SET p.viewCount = p.viewCount + 1
            WHERE p.id = :id
            """)
    int increaseViewCount(@Param("id") Long id);

    // 댓글 수 원자적 증가
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Post p
            SET p.commentCount = p.commentCount + 1
            WHERE p.id = :id
            """)
    int increaseCommentCount(@Param("id") Long id);

    // 댓글 수 원자적 감소
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Post p
            SET p.commentCount = p.commentCount - 1
            WHERE p.id = :id
              AND p.commentCount > 0
            """)
    int decreaseCommentCount(@Param("id") Long id);
}