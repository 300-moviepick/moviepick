package com.moviepick.domain.community.dto.response;

import com.moviepick.domain.community.entity.Post;

import java.time.LocalDateTime;

public record PostUpdateResponse(
        Long postId,
        String title,
        String content,
        LocalDateTime updatedAt
) {
    public static PostUpdateResponse from(Post post) {
        return new PostUpdateResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getUpdatedAt()
        );
    }
}
