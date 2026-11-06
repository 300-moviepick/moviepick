package com.moviepick.domain.community.service;

import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.global.config.CacheNames;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * 게시글 인기순(검색어 없음) 목록 캐시.
 * - PostService 내부 호출로는 @Cacheable 프록시가 동작하지 않으므로 별도 빈으로 둔다.
 * - page 0~4만 저장한다. 그 밖의 페이지는 캐시 없이 PostService를 그대로 호출한다.
 * - 무효화 없음. TTL(3분) 만료에만 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class PostListCacheService {

    private static final String POPULAR = "popular";

    private final PostService postService;

    @Cacheable(
            cacheNames = CacheNames.POSTS_BY_POPULAR,
            key = "'popular-' + #page + '-' + #size",
            sync = true,
            condition = "#page < T(com.moviepick.global.config.CacheNames).MAX_CACHED_PAGE"
    )
    public PageResponse<PostListResponse> getPopularPosts(int page, int size) {
        // Page(PageImpl)는 캐시 직렬화가 안 되므로 PageResponse로 바꿔 저장한다
        return PageResponse.from(
                postService.getPosts(null, POPULAR, PageRequest.of(page, size))
        );
    }
}