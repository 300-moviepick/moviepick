package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MovieRankingRepository {

    Page<MovieRankingResponse> findRanking(Pageable pageable);
}