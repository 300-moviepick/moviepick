
package com.moviepick.domain.community.controller;

import com.moviepick.domain.community.dto.response.PostLikeResponse;
import com.moviepick.domain.community.service.PostLikeService;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/posts/{postId}/likes")
@RequiredArgsConstructor
public class PostLikeController {

    private final PostLikeService postLikeService;

    // 게시글 좋아요 등록
    @PostMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> addLike(
            @LoginMember Long memberId,
            @PathVariable Long postId
    ) {
        PostLikeResponse response = postLikeService.addLike(memberId, postId);

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 게시글 좋아요 취소
    @DeleteMapping
    public ResponseEntity<ApiResponse<PostLikeResponse>> removeLike(
            @LoginMember Long memberId,
            @PathVariable Long postId
    ) {
        PostLikeResponse response = postLikeService.removeLike(memberId, postId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
