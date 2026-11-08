package com.moviepick.domain.report.controller;

import com.moviepick.domain.report.dto.request.CommunityReportRequest;
import com.moviepick.domain.report.dto.response.CommunityReportResponse;
import com.moviepick.domain.report.service.CommunityReportService;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 신고 접수 주소를 한 곳에 모은다 (게시글·댓글은 /api/community, 리뷰는 /api/reviews 아래)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommunityReportController {

    private final CommunityReportService communityReportService;

    // 게시글 신고
    @PostMapping("/community/posts/{postId}/reports")
    public ResponseEntity<ApiResponse<CommunityReportResponse>> reportPost(
            @LoginMember Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody CommunityReportRequest request
    ) {
        CommunityReportResponse response =
                communityReportService.reportPost(
                        memberId,
                        postId,
                        request.getReason(),
                        request.getDetail()
                );

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 댓글 신고
    @PostMapping("/community/comments/{commentId}/reports")
    public ResponseEntity<ApiResponse<CommunityReportResponse>> reportComment(
            @LoginMember Long memberId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommunityReportRequest request
    ) {
        CommunityReportResponse response =
                communityReportService.reportComment(
                        memberId,
                        commentId,
                        request.getReason(),
                        request.getDetail()
                );

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 리뷰 신고 (본문·사유는 게시글 신고와 같음)
    @PostMapping("/reviews/{reviewId}/reports")
    public ResponseEntity<ApiResponse<CommunityReportResponse>> reportReview(
            @PathVariable Long reviewId,
            @LoginMember Long memberId,
            @Valid @RequestBody CommunityReportRequest request
    ) {
        CommunityReportResponse response =
                communityReportService.reportReview(
                        memberId,
                        reviewId,
                        request.getReason(),
                        request.getDetail()
                );

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }
}
