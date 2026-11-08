package com.moviepick.domain.community.dto.response;

import com.moviepick.domain.community.entity.Post;

import java.time.LocalDateTime;

public record PostCreateResponse(
        Long postId,
        String title,
        LocalDateTime createdAt
) {

    public static PostCreateResponse from(Post post) {
        return new PostCreateResponse(
                post.getId(),
                post.getTitle(),
                post.getCreatedAt()
        );
    }
}
