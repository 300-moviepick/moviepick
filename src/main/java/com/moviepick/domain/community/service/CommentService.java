package com.moviepick.domain.community.service;

import com.moviepick.domain.community.dto.request.CommentCreateRequest;
import com.moviepick.domain.community.dto.request.CommentUpdateRequest;
import com.moviepick.domain.community.dto.response.CommentCreateResponse;
import com.moviepick.domain.community.dto.response.CommentListResponse;
import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.community.entity.CommentStatus;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.community.exception.CommentErrorCode;
import com.moviepick.domain.community.exception.PostErrorCode;
import com.moviepick.domain.community.repository.CommentRepository;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    // 댓글 목록에 나오는 상태
    private static final List<CommentStatus> LISTED_STATUSES =
            List.of(CommentStatus.VISIBLE, CommentStatus.BLINDED);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final CommentLikeService commentLikeService;

    // 댓글 등록
    @Transactional
    public CommentCreateResponse createComment(
            Long memberId,
            Long postId,
            CommentCreateRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        Post post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 댓글 수 원자적 증가를 먼저 실행해 게시글 줄 쓰기 잠금을 먼저 잡는다 (insert가 실패하면 함께 롤백)
        postRepository.increaseCommentCount(postId);

        Comment comment = Comment.create(post, member, request.content());
        Comment savedComment = commentRepository.save(comment);

        return CommentCreateResponse.from(savedComment);
    }

    // 댓글 목록 조회
    public Page<CommentListResponse> getComments(
            Long postId,
            Long memberId,
            Pageable pageable
    ) {
        postRepository.findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 숨긴 댓글도 자리를 유지한다 (B1)
        Page<Comment> comments = commentRepository.findByPostIdAndStatusIn(
                postId,
                LISTED_STATUSES,
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))
                )
        );

        // 좋아요 수와 내 좋아요 여부는 페이지 단위로 한 번씩만 조회 (댓글마다 조회하지 않음)
        List<Long> commentIds = comments.map(Comment::getId).getContent();
        Map<Long, Long> likeCounts =
                commentLikeService.countLikesByCommentIds(commentIds);
        Set<Long> likedIds =
                commentLikeService.findLikedCommentIds(memberId, commentIds);

        return comments.map(comment -> CommentListResponse.from(
                comment,
                likeCounts.getOrDefault(comment.getId(), 0L),
                likedIds.contains(comment.getId())
        ));
    }

    // 댓글 삭제 (댓글 ID 기준)
    @Transactional
    public void deleteCommentById(Long memberId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND));

        Long postId = comment.getPost().getId();

        // 게시글이 공개 상태일 때만 댓글 삭제 허용
        postRepository.findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 숨긴 댓글은 작성자 본인도 수정·삭제할 수 없음
        if (comment.isBlinded()) {
            throw new BusinessException(CommentErrorCode.COMMENT_BLINDED);
        }

        if (!comment.getMember().getId().equals(memberId)) {
            throw new BusinessException(CommentErrorCode.COMMENT_ACCESS_DENIED);
        }

        if (comment.getStatus() != CommentStatus.VISIBLE) {
            throw new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND);
        }

        comment.delete();

        // 댓글 수 원자적 감소
        postRepository.decreaseCommentCount(postId);
    }

    /**
     * 댓글 숨김 공통 처리: 관리자 직접 숨김(PATCH /api/admin/comments/{commentId}/blind)과 댓글 신고 승인이 함께 쓴다.
     * 공개(VISIBLE) 댓글만 BLINDED로 바꾸고 게시글 댓글 수를 1 줄인다. 이미 숨김·삭제된 댓글은 그대로 둔다 (반복 호출도 성공).
     * 신고 상태와 회원 제재는 건드리지 않는다.
     *
     * 댓글 수 감소 쿼리(decreaseCommentCount)는 flushAutomatically로 실행 전에 숨김 변경을 DB에 반영하고,
     * clearAutomatically로 실행 후 영속성 컨텍스트를 비운다. 그래서 같은 트랜잭션의 다른 변경을 마친 뒤 마지막에 호출한다.
     */
    @Transactional
    public void blindComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND)
                );

        if (comment.blind()) {
            postRepository.decreaseCommentCount(comment.getPost().getId());
        }
    }

    // 댓글 수정
    @Transactional
    public CommentCreateResponse updateComment(
            Long memberId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND));

        // 게시글 번호는 댓글에서 꺼낸다
        Long postId = comment.getPost().getId();

        // 게시글이 공개 상태일 때만 댓글 수정 허용
        postRepository.findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 숨긴 댓글은 작성자 본인도 수정·삭제할 수 없음
        if (comment.isBlinded()) {
            throw new BusinessException(CommentErrorCode.COMMENT_BLINDED);
        }

        if (!comment.getMember().getId().equals(memberId)) {
            throw new BusinessException(CommentErrorCode.COMMENT_ACCESS_DENIED);
        }

        if (comment.getStatus() != CommentStatus.VISIBLE) {
            throw new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND);
        }

        comment.update(request.content());

        return CommentCreateResponse.from(comment);
    }
}