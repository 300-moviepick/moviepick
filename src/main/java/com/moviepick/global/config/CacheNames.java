package com.moviepick.global.config;

/**
 * Redis 캐시 이름과 공통 캐시 범위.
 * - 이름을 추가하면 RedisConfig에 TTL을 함께 등록한다.
 * - movieRanking, movieExtras는 기존 서비스가 문자열로 쓰고 있어 참조용으로만 둔다.
 */
public final class CacheNames {

    public static final String MOVIE_RANKING = "movieRanking";
    public static final String MOVIE_EXTRAS = "movieExtras";

    /** GET /api/movies/reviews?sort=likes 결과 페이지 */
    public static final String REVIEWS_BY_LIKES = "reviewsByLikes";

    /** GET /api/community/posts?sort=popular (검색어 없음) 결과 페이지 */
    public static final String POSTS_BY_POPULAR = "postsByPopular";

    /** 목록 캐시는 page 0 ~ MAX_CACHED_PAGE-1 (0~4)만 저장한다 */
    public static final int MAX_CACHED_PAGE = 5;

    private CacheNames() {
    }
}
