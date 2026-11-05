
package com.moviepick.global.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

class RedisCacheErrorHandlerTest {

    private RedisCacheErrorHandler errorHandler;
    private Cache cache;

    @BeforeEach
    void setUp() {
        errorHandler = new RedisCacheErrorHandler();
        cache = mock(Cache.class);
    }

    @Test
    void 캐시_조회_실패시_예외를_전파하지_않는다() {
        RuntimeException exception =
                new RuntimeException("Redis GET 실패");

        errorHandler.handleCacheGetError(exception, cache, "test-key");

        verify(cache).getName();
    }

    @Test
    void 캐시_저장_실패시_예외를_전파하지_않는다() {
        RuntimeException exception =
                new RuntimeException("Redis PUT 실패");

        errorHandler.handleCachePutError(
                exception, cache, "test-key", "test-value"
        );

        verify(cache).getName();
    }

    @Test
    void 캐시_삭제_실패시_예외를_전파하지_않는다() {
        RuntimeException exception =
                new RuntimeException("Redis EVICT 실패");

        errorHandler.handleCacheEvictError(exception, cache, "test-key");

        verify(cache).getName();
    }

    @Test
    void 캐시_전체삭제_실패시_예외를_전파하지_않는다() {
        RuntimeException exception =
                new RuntimeException("Redis CLEAR 실패");

        errorHandler.handleCacheClearError(exception, cache);

        verify(cache).getName();
    }
}
