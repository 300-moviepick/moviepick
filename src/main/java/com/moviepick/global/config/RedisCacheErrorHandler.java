package com.moviepick.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        log.warn("Redis 캐시 조회 실패. DB 조회로 진행합니다. cache={}, key={}",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCachePutError(
            RuntimeException exception,
            Cache cache,
            Object key,
            Object value
    ) {
        log.warn("Redis 캐시 저장 실패. cache={}, key={}",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCacheEvictError(
            RuntimeException exception,
            Cache cache,
            Object key
    ) {
        log.error("Redis 캐시 삭제 실패. cache={}, key={}",
                cache.getName(), key, exception);
    }

    @Override
    public void handleCacheClearError(
            RuntimeException exception,
            Cache cache
    ) {
        log.error("Redis 캐시 전체 삭제 실패. cache={}",
                cache.getName(), exception);
    }
}
