package com.moviepick.domain.movie.controller;

import com.moviepick.domain.movie.dto.response.MovieDetailResponse;
import com.moviepick.domain.movie.dto.response.MovieExtraResponse;
import com.moviepick.domain.movie.dto.response.MovieListResponse;
import com.moviepick.domain.movie.service.MovieExtraService;
import com.moviepick.domain.movie.service.MovieQueryService;
import com.moviepick.domain.movie.service.MovieReviewReader;
import com.moviepick.global.security.LoginMember;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import com.moviepick.global.response.ApiResponse;
import com.moviepick.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/movies")
public class MovieController {

    private static final String SORT_RELEASE_DATE = "releaseDate";

    private final MovieQueryService movieQueryService;
    private final MovieReviewReader movieReviewReader;
    private final MovieExtraService movieExtraService;

    // 영화 목록 및 검색
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MovieListResponse>>> getMovies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        // 최근 개봉작 순 (sort=releaseDate). 정렬은 Repository 쿼리가 맡는다
        if (sort != null && !sort.isBlank()) {
            if (!SORT_RELEASE_DATE.equalsIgnoreCase(sort)) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }

            Page<MovieListResponse> movies = movieQueryService
                    .getRecentlyReleasedMovies(keyword, PageRequest.of(page, size));

            return ResponseEntity.ok(
                    ApiResponse.ok(PageResponse.from(movies))
            );
        }

        // sort가 없으면 기존 동작 (id 내림차순)
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        Page<MovieListResponse> movies =
                movieQueryService.getMovies(keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok(PageResponse.from(movies))
        );
    }

    // 영화 상세 조회
    @GetMapping("/{movieId}")
    public ResponseEntity<ApiResponse<MovieDetailResponse>> getMovie(
            @PathVariable Long movieId,
            @LoginMember(required = false) Long memberId
    ) {
        Long myReviewId = movieReviewReader
                .findMyReviewId(memberId, movieId)
                .orElse(null);

        return ResponseEntity.ok(ApiResponse.ok(
                movieQueryService.getMovie(movieId, myReviewId)
        ));
    }

    // 영화 예고편·출연진·OTT 확장 정보
    @GetMapping("/{movieId}/extras")
    public ResponseEntity<ApiResponse<MovieExtraResponse>> getMovieExtras(
            @PathVariable Long movieId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(movieExtraService.getExtras(movieId))
        );
    }
}