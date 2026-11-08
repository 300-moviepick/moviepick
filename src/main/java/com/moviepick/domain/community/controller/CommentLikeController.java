package com.moviepick.domain.community.controller;

import com.moviepick.domain.community.dto.response.CommentLikeResponse;
import com.moviepick.domain.community.service.CommentLikeService;
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
@RequestMapping("/api/community/comments/{commentId}/likes")
@RequiredArgsConstructor
public class CommentLikeController {

    private final CommentLikeService commentLikeService;

    // 댓글 좋아요 등록
    @PostMapping
    public ResponseEntity<ApiResponse<CommentLikeResponse>> addLike(
            @LoginMember Long memberId,
            @PathVariable Long commentId
    ) {
        CommentLikeResponse response =
                commentLikeService.addLike(memberId, commentId);

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 댓글 좋아요 취소
    @DeleteMapping
    public ResponseEntity<ApiResponse<CommentLikeResponse>> removeLike(
            @LoginMember Long memberId,
            @PathVariable Long commentId
    ) {
        CommentLikeResponse response =
                commentLikeService.removeLike(memberId, commentId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
