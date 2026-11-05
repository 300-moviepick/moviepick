package com.moviepick.domain.admin.controller;

import com.moviepick.domain.community.service.CommentService;
import com.moviepick.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final CommentService commentService;

    // 관리자: 댓글 숨김 (게시글·리뷰 숨김과 같은 주소 모양, 반복 호출도 200)
    // 숨김 규칙과 댓글 수 감소는 댓글 신고 승인과 같은 코드 경로(CommentService.blindComment)
    @PatchMapping("/{commentId}/blind")
    public ResponseEntity<ApiResponse<Void>> blindComment(
            @PathVariable Long commentId
    ) {
        commentService.blindComment(commentId);

        return ResponseEntity.ok(ApiResponse.ok());
    }
}
