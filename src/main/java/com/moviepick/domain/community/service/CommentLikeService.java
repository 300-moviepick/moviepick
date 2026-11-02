
package com.moviepick.domain.community.service;

import com.moviepick.domain.community.dto.response.CommentLikeResponse;
import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentLike;
import com.moviepick.domain.community.entity.CommentStatus;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.community.exception.CommentErrorCode;
import com.moviepick.domain.community.repository.CommentLikeRepository;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentLikeService {

    private static final String COMMENT_LIKE_UNIQUE_CONSTRAINT =
            "uk_comment_like_member";

    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;

    // 댓글 좋아요 등록
    @Transactional
    public CommentLikeResponse addLike(Long memberId, Long commentId) {
        Comment comment = findVisibleComment(commentId);

        // 본인 댓글 좋아요 금지 (리뷰와 같은 400)
        if (comment.getMember().getId().equals(memberId)) {
            throw new BusinessException(CommentErrorCode.CANNOT_LIKE_OWN_COMMENT);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.NOT_FOUND));

        // 이미 좋아요를 등록한 경우
        if (commentLikeRepository.existsByCommentIdAndMemberId(
                commentId, memberId)) {
            throw new BusinessException(
                    CommentErrorCode.COMMENT_ALREADY_LIKED);
        }

        CommentLike commentLike = CommentLike.create(comment, member);

        try {
            commentLikeRepository.saveAndFlush(commentLike);
        } catch (DataIntegrityViolationException exception) {
            if (isCommentLikeUniqueConstraintViolation(exception)) {
                throw new BusinessException(
                        CommentErrorCode.COMMENT_ALREADY_LIKED);
            }

            throw exception;
        }

        long likeCount = commentLikeRepository.countByCommentId(commentId);

        return new CommentLikeResponse(true, likeCount);
    }

    // 댓글 좋아요 취소
    @Transactional
    public CommentLikeResponse removeLike(Long memberId, Long commentId) {
        findVisibleComment(commentId);

        CommentLike commentLike = commentLikeRepository
                .findByCommentIdAndMemberId(commentId, memberId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommentErrorCode.COMMENT_LIKE_NOT_FOUND));

        commentLikeRepository.delete(commentLike);

        long likeCount = commentLikeRepository.countByCommentId(commentId);

        return new CommentLikeResponse(false, likeCount);
    }

    // 목록용: 댓글 ID별 좋아요 수. 좋아요가 없는 댓글은 맵에 없으므로 getOrDefault(id, 0L)로 꺼낸다
    public Map<Long, Long> countLikesByCommentIds(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentLikeRepository.countByCommentIds(commentIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    // 목록용: 댓글 중 회원이 좋아요한 댓글 ID (비로그인이면 빈 집합)
    public Set<Long> findLikedCommentIds(Long memberId, List<Long> commentIds) {
        if (memberId == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                commentLikeRepository.findLikedCommentIds(memberId, commentIds));
    }

    // 특정 회원의 댓글 좋아요 여부 조회
    public boolean isLikedByMember(Long commentId, Long memberId) {
        return commentLikeRepository
                .existsByCommentIdAndMemberId(commentId, memberId);
    }

    // 댓글 및 소속 게시글의 공개 상태 확인
    private Comment findVisibleComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(
                                CommentErrorCode.COMMENT_NOT_FOUND));

        if (comment.getPost().getStatus() != PostStatus.VISIBLE) {
            throw new BusinessException(
                    CommentErrorCode.COMMENT_NOT_FOUND);
        }

        // 숨긴 댓글은 좋아요·좋아요 취소를 막는다 (목록에 자리가 남아 있으므로 "없음"과 구분)
        if (comment.isBlinded()) {
            throw new BusinessException(
                    CommentErrorCode.COMMENT_BLINDED);
        }

        if (comment.getStatus() != CommentStatus.VISIBLE) {
            throw new BusinessException(
                    CommentErrorCode.COMMENT_NOT_FOUND);
        }

        return comment;
    }

    // 댓글 좋아요 유니크 제약 위반인지 확인
    private boolean isCommentLikeUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintException) {
                String name = constraintException.getConstraintName();
                return name != null
                        && name.toLowerCase().contains(COMMENT_LIKE_UNIQUE_CONSTRAINT);
            }

            cause = cause.getCause();
        }

        return false;
    }
}
