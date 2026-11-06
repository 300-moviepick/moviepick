package com.moviepick.domain.review.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.moviepick.domain.review.dto.request.ReviewSort;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.global.config.CacheNames;
import com.moviepick.global.response.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** 전체 리뷰 좋아요순 캐시: page 0~4는 같은 page·size면 한 번만 조회하고, 그 밖은 매번 조회한다 */
@SpringJUnitConfig
class ReviewListCacheServiceTest {

    @Configuration
    @EnableCaching
    @Import(ReviewListCacheService.class)
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.REVIEWS_BY_LIKES);
        }
    }

    @Autowired
    private ReviewListCacheService reviewListCacheService;
    @Autowired
    private CacheManager cacheManager;
    @MockitoBean
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        cacheManager.getCache(CacheNames.REVIEWS_BY_LIKES).clear();
        given(reviewService.getReviews(eq(ReviewSort.LIKES), anyInt(), anyInt()))
                .willReturn(new PageResponse<ReviewWithMovieResponse>(List.of(), 0, 20, 0, 0));
    }

    @Test
    @DisplayName("같은 page·size를 두 번 조회하면 ReviewService는 한 번만 호출된다")
    void samePageIsCached() {
        reviewListCacheService.getReviewsByLikes(0, 20);
        reviewListCacheService.getReviewsByLikes(0, 20);

        verify(reviewService, times(1)).getReviews(ReviewSort.LIKES, 0, 20);
    }

    @Test
    @DisplayName("page나 size가 다르면 각각 따로 조회한다")
    void differentPageOrSizeIsSeparate() {
        reviewListCacheService.getReviewsByLikes(0, 20);
        reviewListCacheService.getReviewsByLikes(1, 20);
        reviewListCacheService.getReviewsByLikes(0, 10);

        verify(reviewService, times(1)).getReviews(ReviewSort.LIKES, 0, 20);
        verify(reviewService, times(1)).getReviews(ReviewSort.LIKES, 1, 20);
        verify(reviewService, times(1)).getReviews(ReviewSort.LIKES, 0, 10);
    }

    @Test
    @DisplayName("page 5는 캐시 범위 밖이라 두 번 조회하면 두 번 호출된다")
    void pageOutOfRangeIsNotCached() {
        reviewListCacheService.getReviewsByLikes(5, 20);
        reviewListCacheService.getReviewsByLikes(5, 20);

        verify(reviewService, times(2)).getReviews(ReviewSort.LIKES, 5, 20);
    }
}
