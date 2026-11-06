package com.moviepick.domain.review.service;

import com.moviepick.domain.review.dto.request.ReviewSort;
import com.moviepick.domain.review.dto.response.ReviewWithMovieResponse;
import com.moviepick.global.config.CacheNames;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 전체 리뷰 좋아요순 목록 캐시 (page 0~4, TTL 3분).
 * 같은 클래스 안 호출은 캐시가 동작하지 않으므로 ReviewService와 분리한다.
 * 응답에 회원별 필드가 없어 페이지 결과 전체를 캐시한다. 무효화하지 않고 TTL 만료에만 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class ReviewListCacheService {

    private final ReviewService reviewService;

    @Cacheable(
            cacheNames = CacheNames.REVIEWS_BY_LIKES,
            key = "'likes-' + #page + '-' + #size",
            sync = true,
            condition = "#page < T(com.moviepick.global.config.CacheNames).MAX_CACHED_PAGE"
    )
    public PageResponse<ReviewWithMovieResponse> getReviewsByLikes(int page, int size) {
        return reviewService.getReviews(ReviewSort.LIKES, page, size);
    }
}
