package com.moviepick.domain.community.repository;

import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 목록에 보일 상태들의 댓글 페이지 조회 (좋아요 수·내 좋아요 여부는 CommentLikeRepository로 따로)
    Page<Comment> findByPostIdAndStatusIn(
            Long postId,
            Collection<CommentStatus> statuses,
            Pageable pageable
    );
}
