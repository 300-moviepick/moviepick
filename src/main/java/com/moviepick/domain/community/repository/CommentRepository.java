
package com.moviepick.domain.community.repository;

import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 특정 게시글의 댓글 목록 조회 (오래된 댓글부터)
    List<Comment> findAllByPostIdAndStatusOrderByCreatedAtAsc(
            Long postId,
            CommentStatus status
    );

    // 특정 게시글의 댓글 목록 페이지 조회 (오래된 댓글부터)
    Page<Comment> findAllByPostIdAndStatusOrderByCreatedAtAsc(
            Long postId,
            CommentStatus status,
            Pageable pageable
    );

    // 목록에 보일 상태들의 댓글 페이지 조회 (좋아요 수·내 좋아요 여부는 CommentLikeRepository로 따로)
    Page<Comment> findByPostIdAndStatusIn(
            Long postId,
            Collection<CommentStatus> statuses,
            Pageable pageable
    );
}
