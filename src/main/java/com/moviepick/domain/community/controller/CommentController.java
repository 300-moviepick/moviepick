package com.moviepick.domain.community.controller;

import com.moviepick.domain.community.dto.request.CommentCreateRequest;
import com.moviepick.domain.community.dto.request.CommentUpdateRequest;
import com.moviepick.domain.community.dto.response.CommentCreateResponse;
import com.moviepick.domain.community.dto.response.CommentListResponse;
import com.moviepick.domain.community.service.CommentService;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // 댓글 등록
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentCreateResponse>> createComment(
            @LoginMember Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        CommentCreateResponse response =
                commentService.createComment(memberId, postId, request);

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 댓글 목록 조회
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentListResponse>>> getComments(
            @LoginMember(required = false) Long memberId,
            @PathVariable Long postId,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        Page<CommentListResponse> response =
                commentService.getComments(postId, memberId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(response))
        );
    }

    // 댓글 수정 (삭제·좋아요·신고와 같은 /comments/{commentId} 주소)
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentCreateResponse>> updateComment(
            @LoginMember Long memberId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        CommentCreateResponse response =
                commentService.updateComment(
                        memberId, commentId, request
                );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 댓글 삭제
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @LoginMember Long memberId,
            @PathVariable Long commentId
    ) {
        commentService.deleteCommentById(memberId, commentId);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
