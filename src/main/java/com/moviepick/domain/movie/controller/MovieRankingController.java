package com.moviepick.domain.movie.controller;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.domain.movie.service.MovieRankingService;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieRankingController {

    private final MovieRankingService movieRankingService;

    // 영화 랭킹 조회
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<PageResponse<MovieRankingResponse>>> getRanking(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        movieRankingService.getRanking(page, size)
                )
        );
    }
}