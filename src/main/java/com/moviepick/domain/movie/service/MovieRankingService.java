package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.domain.movie.repository.MovieRankingRepository;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieRankingService {

    private final MovieRankingRepository movieRankingRepository;

    @Cacheable(
            cacheNames = "movieRanking",
            key = "#page + '-' + #size"
    )
    public PageResponse<MovieRankingResponse> getRanking(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);

        Page<MovieRankingResponse> rankingPage =
                movieRankingRepository.findRanking(pageable);

        return PageResponse.from(rankingPage);
    }
}
