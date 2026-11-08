package com.moviepick.global.config;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    // 좋아요순·인기순 목록 캐시 TTL. 무효화 없이 만료에만 맡긴다
    static final Duration LIST_CACHE_TTL = Duration.ofMinutes(3);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory
    ) {
        var valueSerializer = cacheValueSerializer();

        RedisCacheConfiguration configuration =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(5))
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(new StringRedisSerializer())
                        )
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair
                                        .fromSerializer(valueSerializer)
                        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .withCacheConfiguration(CacheNames.MOVIE_RANKING,
                        configuration.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(CacheNames.MOVIE_EXTRAS,
                        configuration.entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(CacheNames.REVIEWS_BY_LIKES,
                        configuration.entryTtl(LIST_CACHE_TTL))
                .withCacheConfiguration(CacheNames.POSTS_BY_POPULAR,
                        configuration.entryTtl(LIST_CACHE_TTL))
                .transactionAware()
                .build();
    }

    // 캐시 값 직렬화기. 캐시에 넣는 타입의 패키지는 이 허용 목록에 있어야 한다
    static GenericJacksonJsonRedisSerializer cacheValueSerializer() {
        var typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.moviepick.global.response.")
                .allowIfSubType("com.moviepick.domain.movie.dto.response.")
                .allowIfSubType("com.moviepick.domain.review.dto.response.")
                .allowIfSubType("com.moviepick.domain.community.dto.response.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }

    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        log.info("RedisCacheErrorHandler 등록");
        return new RedisCacheErrorHandler();
    }
}
