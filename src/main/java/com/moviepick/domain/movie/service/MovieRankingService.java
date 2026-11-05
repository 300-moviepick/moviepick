
package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieRankingService {

    private final MovieRankingVersionService movieRankingVersionService;
    private final MovieRankingCacheService movieRankingCacheService;

    public PageResponse<MovieRankingResponse> getRanking(int page, int size) {

        long version;

        try {
            // Redis에서 현재 랭킹 버전 조회
            version = movieRankingVersionService.getVersion();

        } catch (Exception e) {
            // Redis 버전 조회 실패 시 DB에서 직접 조회
            log.warn("Redis 버전 조회 실패. DB에서 직접 조회합니다.", e);

            PageResponse<MovieRankingResponse> result =
                    movieRankingCacheService.getRankingWithoutCache(page, size);

            log.info("Redis 장애 fallback DB 조회 성공");

            return result;
        }

        // Redis 버전 조회 성공 시 캐시를 사용하는 랭킹 조회
        // DB 조회 오류는 여기서 별도로 잡지 않고 그대로 전달
        return movieRankingCacheService.getRanking(version, page, size);
    }
}
