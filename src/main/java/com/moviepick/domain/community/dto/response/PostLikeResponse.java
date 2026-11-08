package com.moviepick.domain.community.dto.response;

public record PostLikeResponse(
        boolean liked,
        long likeCount
) {
}
