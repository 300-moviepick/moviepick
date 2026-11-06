package com.moviepick.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.global.config.CacheNames;
import com.moviepick.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 인기순 캐시: 같은 page·size는 PostService를 1번만 부르고, page 5 이상은 매번 부른다 (Redis 대신 메모리 캐시) */
@SpringJUnitConfig(PostListCacheServiceTest.CacheTestConfig.class)
class PostListCacheServiceTest {

    @Configuration
    @EnableCaching
    @Import(PostListCacheService.class)
    static class CacheTestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.POSTS_BY_POPULAR);
        }
    }

    @Autowired
    private PostListCacheService postListCacheService;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private PostService postService;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheNames.POSTS_BY_POPULAR).clear();

        given(postService.getPosts(isNull(), eq("popular"), any(Pageable.class)))
                .willAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(2);
                    PostListResponse post = new PostListResponse(1L, "제목",
                            new PostListResponse.Author(10L, "작성자"),
                            5L, 0, 0, LocalDateTime.of(2026, 11, 6, 9, 0));
                    return new PageImpl<>(List.of(post), pageable, 1);
                });
    }

    @Test
    void 같은_페이지를_두_번_조회하면_DB_조회는_한_번이다() {
        PageResponse<PostListResponse> first = postListCacheService.getPopularPosts(0, 20);
        PageResponse<PostListResponse> second = postListCacheService.getPopularPosts(0, 20);

        assertThat(second).isEqualTo(first);
        verify(postService, times(1)).getPosts(isNull(), eq("popular"), eq(PageRequest.of(0, 20)));
    }

    @Test
    void page와_size가_다르면_따로_캐시된다() {
        postListCacheService.getPopularPosts(0, 20);
        postListCacheService.getPopularPosts(1, 20);
        postListCacheService.getPopularPosts(0, 10);

        verify(postService, times(3)).getPosts(isNull(), eq("popular"), any(Pageable.class));
    }

    @Test
    void page_5_이상은_캐시하지_않는다() {
        postListCacheService.getPopularPosts(5, 20);
        postListCacheService.getPopularPosts(5, 20);

        verify(postService, times(2)).getPosts(isNull(), eq("popular"), eq(PageRequest.of(5, 20)));
    }
}