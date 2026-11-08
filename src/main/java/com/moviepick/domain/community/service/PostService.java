package com.moviepick.domain.community.service;

import com.moviepick.domain.community.dto.request.PostCreateRequest;
import com.moviepick.domain.community.dto.request.PostUpdateRequest;
import com.moviepick.domain.community.dto.response.PostCreateResponse;
import com.moviepick.domain.community.dto.response.PostDetailResponse;
import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.domain.community.dto.response.PostUpdateResponse;
import com.moviepick.domain.community.entity.Post;
import com.moviepick.domain.community.entity.PostStatus;
import com.moviepick.domain.community.exception.PostErrorCode;
import com.moviepick.domain.community.repository.PostRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.exception.MemberErrorCode;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PostLikeService postLikeService;

    // 게시글 작성
    @Transactional
    public PostCreateResponse create(Long memberId, PostCreateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        Post post = Post.create(
                member,
                request.title().trim(),
                request.content()
        );

        Post savedPost = postRepository.save(post);

        return PostCreateResponse.from(savedPost);
    }

    // 게시글 목록 조회
    public Page<PostListResponse> getPosts(
            String q,
            String sort,
            Pageable pageable
    ) {
        String sortType = (sort == null || sort.isBlank())
                ? "latest"
                : sort;

        boolean hasKeyword = q != null && !q.isBlank();
        String keyword = hasKeyword ? q.trim() : null;

        // 검색어의 %, _, \를 LIKE 검색의 일반 문자로 처리
        if (keyword != null) {
            keyword = keyword
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
        }

        // 인기순: 전체 좋아요 수로 정렬해야 하므로 게시글과 좋아요 수를 함께 조회
        if ("popular".equalsIgnoreCase(sortType)) {
            Page<Object[]> results;

            if (hasKeyword) {
                results = postRepository.searchPopularPostsWithLikeCount(
                        PostStatus.VISIBLE,
                        keyword,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize()
                        )
                );
            } else {
                results = postRepository.findPopularPostsWithLikeCount(
                        PostStatus.VISIBLE,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize()
                        )
                );
            }

            return results.map(row -> {
                Post post = (Post) row[0];
                long likeCount = ((Number) row[1]).longValue();

                return PostListResponse.from(post, likeCount);
            });
        }

        // 최신순: 인덱스(status, created_at)로 게시글 페이지만 먼저 조회
        PageRequest latestPage = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        Page<Post> posts = hasKeyword
                ? postRepository.searchByStatus(PostStatus.VISIBLE, keyword, latestPage)
                : postRepository.findByStatus(PostStatus.VISIBLE, latestPage);

        // 그 페이지의 좋아요 수만 한 번에 조회
        Map<Long, Long> likeCounts = postLikeService.countLikesByPostIds(
                posts.map(Post::getId).getContent());

        return posts.map(post -> PostListResponse.from(
                post,
                likeCounts.getOrDefault(post.getId(), 0L)
        ));
    }

    // 게시글 상세 조회
    // 관리자는 숨긴 글(BLINDED)도 볼 수 있다. 삭제된 글은 관리자도 404
    @Transactional
    public PostDetailResponse getPost(Long memberId, Long postId, boolean admin) {
        if (admin) {
            Post blinded = postRepository.findById(postId)
                    .filter(found -> found.getStatus() == PostStatus.BLINDED)
                    .orElse(null);

            // 숨긴 글은 조회수를 올리지 않는다
            if (blinded != null) {
                long likeCount = postLikeService.countLikes(postId);
                boolean likedByMe = memberId != null
                        && postLikeService.isLikedByMember(postId, memberId);

                return PostDetailResponse.from(blinded, likeCount, likedByMe);
            }
        }

        Post post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 조회수 원자적 증가
        postRepository.increaseViewCount(postId);

        // 증가된 조회수를 응답에 반영
        post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 좋아요 수 조회 (공개 글인지는 위에서 이미 확인했다)
        long likeCount = postLikeService.countLikes(postId);

        // 비로그인 사용자는 좋아요 여부를 false로 처리
        boolean likedByMe = memberId != null
                && postLikeService.isLikedByMember(postId, memberId);

        return PostDetailResponse.from(post, likeCount, likedByMe);
    }

    // 게시글 수정
    @Transactional
    public PostUpdateResponse updatePost(
            Long memberId,
            Long postId,
            PostUpdateRequest request
    ) {
        Post post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 작성자 본인인지 확인
        if (!post.getMember().getId().equals(memberId)) {
            throw new BusinessException(PostErrorCode.POST_ACCESS_DENIED);
        }

        // 제목과 내용 중 최소 하나는 입력해야 함
        if (request.title() == null && request.content() == null) {
            throw new BusinessException(PostErrorCode.POST_INVALID_INPUT);
        }

        // 입력된 제목이 공백만으로 이루어진 경우
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(PostErrorCode.POST_INVALID_INPUT);
        }

        // 입력된 내용이 공백만으로 이루어진 경우
        if (request.content() != null && request.content().isBlank()) {
            throw new BusinessException(PostErrorCode.POST_INVALID_INPUT);
        }

        // 전달된 필드만 수정하고 생략한 필드는 기존 값 유지
        String title = request.title() != null
                ? request.title().trim()
                : post.getTitle();

        String content = request.content() != null
                ? request.content()
                : post.getContent();

        post.update(title, content);

        // updatedAt을 DB에 반영한 후 응답 생성
        postRepository.flush();

        return PostUpdateResponse.from(post);
    }

    // 게시글 삭제 (논리 삭제)
    @Transactional
    public void deletePost(Long memberId, Long postId) {
        Post post = postRepository
                .findByIdAndStatus(postId, PostStatus.VISIBLE)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 작성자 본인인지 확인
        if (!post.getMember().getId().equals(memberId)) {
            throw new BusinessException(PostErrorCode.POST_ACCESS_DENIED);
        }

        post.delete();
    }

    // 관리자: 게시글 수동 숨김
    @Transactional
    public void blindByAdmin(Long postId) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() ->
                        new BusinessException(PostErrorCode.POST_NOT_FOUND));

        // 이미 삭제된 게시글은 숨김 처리하지 않음
        if (post.getStatus() == PostStatus.DELETED) {
            throw new BusinessException(PostErrorCode.POST_NOT_FOUND);
        }

        // 게시글 숨김 처리
        post.blind();
    }
}
