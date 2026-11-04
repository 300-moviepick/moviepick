
package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.domain.movie.repository.MovieRankingRepository;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieRankingCacheService {

    private final MovieRankingRepository movieRankingRepository;

    // Redis 캐시를 사용하는 랭킹 조회
    @Cacheable(
            cacheNames = "movieRanking",
            key = "#version + '-' + #page + '-' + #size",
            unless = "#result.content.isEmpty()"
    )
    public PageResponse<MovieRankingResponse> getRanking(
            long version,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<MovieRankingResponse> rankingPage =
                movieRankingRepository.findRanking(pageable);

        return PageResponse.from(rankingPage);
    }

    // Redis 캐시를 사용하지 않고 DB에서 직접 랭킹 조회
    public PageResponse<MovieRankingResponse> getRankingWithoutCache(
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<MovieRankingResponse> rankingPage =
                movieRankingRepository.findRanking(pageable);

        return PageResponse.from(rankingPage);
    }
}
