package com.moviepick.domain.community.dto.response;

import com.moviepick.domain.community.entity.Comment;

import java.time.LocalDateTime;

public record CommentCreateResponse(
        Long commentId,
        Long postId,
        String content,
        LocalDateTime createdAt
) {
    public static CommentCreateResponse from(Comment comment) {
        return new CommentCreateResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
