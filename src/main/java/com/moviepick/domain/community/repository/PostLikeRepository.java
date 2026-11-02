package com.moviepick.domain.community.repository;

import com.moviepick.domain.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    // 특정 회원이 특정 게시글에 좋아요를 눌렀는지 확인
    boolean existsByPostIdAndMemberId(Long postId, Long memberId);

    // 특정 회원이 특정 게시글에 누른 좋아요 조회
    Optional<PostLike> findByPostIdAndMemberId(Long postId, Long memberId);

    // 게시글의 좋아요 수 조회
    long countByPostId(Long postId);

    // 목록용: 한 페이지 게시글의 좋아요 수를 쿼리 한 번으로. 좋아요가 없는 게시글은 결과에 없다
    @Query("""
            SELECT pl.post.id, COUNT(pl)
            FROM PostLike pl
            WHERE pl.post.id IN :postIds
            GROUP BY pl.post.id
            """)
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);
}