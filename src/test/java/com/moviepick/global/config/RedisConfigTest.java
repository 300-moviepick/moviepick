package com.moviepick.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.moviepick.domain.community.dto.response.PostListResponse;
import com.moviepick.domain.review.dto.response.AuthorResponse;
import com.moviepick.domain.review.dto.response.ReviewMovieResponse;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.global.response.PageResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/** 목록 캐시 등록(TTL)과 캐시 값 직렬화 왕복을 Redis 없이 확인한다 */
class RedisConfigTest {

    private final GenericJacksonJsonRedisSerializer serializer = RedisConfig.cacheValueSerializer();

    @Test
    void 목록_캐시_2개가_TTL_3분으로_등록된다() {
        RedisCacheManager cacheManager =
                new RedisConfig().cacheManager(mock(RedisConnectionFactory.class));
        cacheManager.afterPropertiesSet(); // 등록한 캐시 생성 (Redis 연결은 하지 않는다)

        // transactionAware라 캐시가 TransactionAwareCacheDecorator로 감싸져 있다
        assertThat(ttlOf(cacheManager, CacheNames.REVIEWS_BY_LIKES)).isEqualTo(Duration.ofMinutes(3));
        assertThat(ttlOf(cacheManager, CacheNames.POSTS_BY_POPULAR)).isEqualTo(Duration.ofMinutes(3));
        // 기존 캐시 TTL은 그대로
        assertThat(ttlOf(cacheManager, CacheNames.MOVIE_RANKING)).isEqualTo(Duration.ofMinutes(5));
        assertThat(ttlOf(cacheManager, CacheNames.MOVIE_EXTRAS)).isEqualTo(Duration.ofHours(12));
    }

    @Test
    void 리뷰_좋아요순_페이지가_직렬화_왕복된다() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 6, 12, 30, 15, 123456000);
        PageResponse<ReviewWithMovieResponse> page = new PageResponse<>(List.of(
                new ReviewWithMovieResponse(1L, new ReviewMovieResponse(10L, "영화", "/p.jpg"),
                        new AuthorResponse(100L, "작성자"), new BigDecimal("4.5"), "내용",
                        false, 7, createdAt, null, false, false),
                // 숨긴 리뷰: rating·content null
                new ReviewWithMovieResponse(2L, new ReviewMovieResponse(11L, "영화2", null),
                        new AuthorResponse(101L, "탈퇴한 회원"), null, null,
                        true, 0, createdAt, null, true, true)
        ), 0, 20, 2L, 1);

        Object restored = serializer.deserialize(serializer.serialize(page));

        assertThat(restored).isEqualTo(page);
    }

    @Test
    void 게시글_인기순_페이지가_직렬화_왕복된다() {
        PageResponse<PostListResponse> page = new PageResponse<>(List.of(
                new PostListResponse(1L, "제목", new PostListResponse.Author(100L, "작성자"),
                        12L, 3, 50, LocalDateTime.of(2026, 11, 6, 9, 0))
        ), 1, 20, 21L, 2);

        Object restored = serializer.deserialize(serializer.serialize(page));

        assertThat(restored).isEqualTo(page);
    }

    @Test
    void 허용_목록에_없는_패키지_타입은_역직렬화되지_않는다() {
        PageResponse<NotAllowed> page = new PageResponse<>(List.of(new NotAllowed(1L)), 0, 20, 1L, 1);

        byte[] bytes = serializer.serialize(page);

        assertThatThrownBy(() -> serializer.deserialize(bytes))
                .isInstanceOf(SerializationException.class);
    }

    private static Duration ttlOf(RedisCacheManager cacheManager, String name) {
        var decorator = (TransactionAwareCacheDecorator) cacheManager.getCache(name);
        RedisCacheConfiguration configuration =
                ((RedisCache) decorator.getTargetCache()).getCacheConfiguration();
        return configuration.getTtlFunction().getTimeToLive(Object.class, null);
    }

    record NotAllowed(Long id) {
    }
}
