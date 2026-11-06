package com.moviepick.domain.community.controller;

import com.moviepick.domain.community.dto.request.PostCreateRequest;
import com.moviepick.domain.community.dto.request.PostUpdateRequest;
import com.moviepick.domain.community.dto.response.PostCreateResponse;
import com.moviepick.domain.community.dto.response.PostDetailResponse;
import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.domain.community.dto.response.PostUpdateResponse;
import com.moviepick.domain.community.service.PostListCacheService;
import com.moviepick.domain.community.service.PostService;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostListCacheService postListCacheService;

    // 게시글 작성
    @PostMapping
    public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
            @LoginMember Long memberId,
            @Valid @RequestBody PostCreateRequest request
    ) {
        PostCreateResponse response = postService.create(memberId, request);

        return ResponseEntity.status(201)
                .body(ApiResponse.created(response));
    }

    // 게시글 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostListResponse>>> getPosts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "latest") String sort,
            @PageableDefault(page = 0, size = 20) Pageable pageable
    ) {
        // 검색어는 최대 50자
        if (q != null && q.length() > 50) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 허용된 정렬 조건 확인
        if (!"latest".equalsIgnoreCase(sort)
                && !"popular".equalsIgnoreCase(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        PageResponse<PostListResponse> response;

        // 인기순 + 검색어 없음만 캐시 사용 (page 0~4, TTL 3분). 최신순·검색은 기존 그대로
        if ("popular".equalsIgnoreCase(sort) && (q == null || q.isBlank())) {
            response = postListCacheService.getPopularPosts(
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );
        } else {
            response = PageResponse.from(postService.getPosts(q, sort, pageable));
        }

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시글 상세 조회
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(
            @LoginMember(required = false) Long memberId,
            @PathVariable Long postId,
            Authentication authentication
    ) {
        // 관리자는 숨긴 글도 상세 조회 가능
        boolean admin = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        PostDetailResponse response =
                postService.getPost(memberId, postId, admin);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시글 수정
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostUpdateResponse>> updatePost(
            @LoginMember Long memberId,
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        PostUpdateResponse response =
                postService.updatePost(memberId, postId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 게시글 삭제 (논리 삭제)
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @LoginMember Long memberId,
            @PathVariable Long postId
    ) {
        postService.deletePost(memberId, postId);

        return ResponseEntity.ok(ApiResponse.ok());
    }
}