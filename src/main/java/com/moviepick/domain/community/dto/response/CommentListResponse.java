package com.moviepick.domain.community.dto.response;

import com.moviepick.domain.community.entity.Comment;
import com.moviepick.domain.member.entity.MemberStatus;

import java.time.LocalDateTime;

public record CommentListResponse(
        Long commentId,
        Author author,
        String content,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        // 신고 승인으로 숨긴 댓글이면 true (content는 안내 문구, 원문은 보내지 않음)
        boolean blinded
) {

    private static final String BLINDED_CONTENT = "관리자에 의해 가려진 댓글";

    public record Author(
            Long memberId,
            String nickname
    ) {
    }

    public static CommentListResponse from(
            Comment comment,
            long likeCount,
            boolean likedByMe
    ) {
        return new CommentListResponse(
                comment.getId(),
                new Author(
                        comment.getMember().getId(),
                        comment.getMember().getStatus() == MemberStatus.WITHDRAWN
                                ? "탈퇴한 회원"
                                : comment.getMember().getNickname()
                ),
                comment.isBlinded() ? BLINDED_CONTENT : comment.getContent(),
                likeCount,
                likedByMe,
                comment.getCreatedAt(),
                comment.isBlinded()
        );
    }
}
