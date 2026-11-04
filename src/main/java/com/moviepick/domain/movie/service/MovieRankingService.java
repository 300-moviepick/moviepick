
package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieRankingService {

    private final MovieRankingVersionService movieRankingVersionService;
    private final MovieRankingCacheService movieRankingCacheService;

    public PageResponse<MovieRankingResponse> getRanking(int page, int size) {

        try {
            // Redis에서 현재 랭킹 버전 조회
            long version = movieRankingVersionService.getVersion();

            // 정상이라면 Redis 캐시를 사용하는 랭킹 조회
            return movieRankingCacheService.getRanking(version, page, size);

        } catch (Exception e) {
            log.warn("랭킹 Redis 조회 실패. DB에서 직접 조회합니다.", e);

            PageResponse<MovieRankingResponse> result =
                    movieRankingCacheService.getRankingWithoutCache(page, size);

            log.info("Redis 장애 fallback DB 조회 성공");

            return result;
        }
    }
}
