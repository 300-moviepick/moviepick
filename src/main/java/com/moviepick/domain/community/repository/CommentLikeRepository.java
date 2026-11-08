package com.moviepick.domain.community.repository;

import com.moviepick.domain.community.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    // 특정 회원이 특정 댓글에 좋아요를 눌렀는지 확인
    boolean existsByCommentIdAndMemberId(Long commentId, Long memberId);

    // 특정 회원이 특정 댓글에 누른 좋아요 조회
    Optional<CommentLike> findByCommentIdAndMemberId(
            Long commentId,
            Long memberId
    );

    // 특정 댓글의 좋아요 수 조회
    long countByCommentId(Long commentId);

    // 목록용: 한 페이지 댓글의 좋아요 수를 쿼리 한 번으로. 좋아요가 없는 댓글은 결과에 없다
    @Query("""
            SELECT cl.comment.id, COUNT(cl)
            FROM CommentLike cl
            WHERE cl.comment.id IN :commentIds
            GROUP BY cl.comment.id
            """)
    List<Object[]> countByCommentIds(@Param("commentIds") Collection<Long> commentIds);

    // 목록용: 한 페이지 댓글 중 회원이 좋아요한 댓글 ID
    @Query("""
            SELECT cl.comment.id
            FROM CommentLike cl
            WHERE cl.member.id = :memberId
              AND cl.comment.id IN :commentIds
            """)
    List<Long> findLikedCommentIds(
            @Param("memberId") Long memberId,
            @Param("commentIds") Collection<Long> commentIds
    );
}
