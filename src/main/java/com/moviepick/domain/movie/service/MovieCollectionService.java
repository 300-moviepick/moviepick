package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.client.TmdbClient;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieCollectionService {

    private static final int MAX_PAGES = 5;
    private static final int MAX_INITIAL_PAGES = 100;

    private final TmdbClient tmdbClient;
    private final MovieSaveService movieSaveService;

    // 정기 수집: 최대 5페이지
    public MovieSyncResponse collectPopularMoviesWithResult(int requestedPages) {
        return collectMovies(requestedPages, MAX_PAGES);
    }

    // 초기 대량 수집: 최대 100페이지
    public MovieSyncResponse collectInitialPopularMoviesWithResult(int requestedPages) {
        return collectMovies(requestedPages, MAX_INITIAL_PAGES);
    }

    // 공통 영화 수집 처리
    private MovieSyncResponse collectMovies(int requestedPages, int maxPages) {

        if (requestedPages < 1 || requestedPages > maxPages) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        int savedCount = 0;
        int failedCount = 0;
        Integer stoppedPage = null;
        String stopReason = null;

        Set<Long> processedIds = new HashSet<>();

        pageLoop:
        for (int page = 1; page <= requestedPages; page++) {

            TmdbPopularResponse popular;

            // TMDB 인기 영화 목록 조회
            try {
                popular = tmdbClient.getPopularMovies(page);

                if (popular == null || popular.results() == null) {
                    throw new IllegalStateException("TMDB 인기 영화 응답이 비어 있습니다.");
                }

            } catch (RestClientException | IllegalStateException e) {

                MovieErrorCode errorCode = resolveTmdbError(e);

                log.warn("TMDB 인기 영화 목록 조회 실패: page={}, reason={}",
                        page, e.getMessage());

                if (page == 1) {
                    throw new BusinessException(errorCode);
                }

                stoppedPage = page;
                stopReason = errorCode.getMessage();
                break;
            }

            if (popular.results().isEmpty()) {
                break;
            }

            for (TmdbPopularResponse.MovieSummary summary : popular.results()) {

                if (summary == null || summary.id() == null) {
                    failedCount++;
                    log.warn("TMDB 영화 ID 누락: page={}", page);
                    continue;
                }

                Long tmdbId = summary.id();

                // 동일 실행 내 중복 영화 제외
                if (!processedIds.add(tmdbId)) {
                    continue;
                }

                TmdbMovieResponse detail;

                // 영화 상세 정보 조회
                try {
                    detail = tmdbClient.getMovieDetail(tmdbId);

                } catch (RestClientException | IllegalStateException e) {

                    if (e instanceof RestClientResponseException responseException
                            && responseException.getStatusCode().value() == 404) {

                        failedCount++;
                        log.warn("TMDB 영화 상세 정보 없음: tmdbId={}", tmdbId);
                        continue;
                    }

                    MovieErrorCode errorCode = resolveTmdbError(e);

                    log.warn("TMDB 영화 상세 조회 실패: page={}, tmdbId={}, reason={}",
                            page, tmdbId, e.getMessage());

                    if (page == 1) {
                        throw new BusinessException(errorCode);
                    }

                    stoppedPage = page;
                    stopReason = errorCode.getMessage();
                    break pageLoop;
                }

                // 영화별 개별 트랜잭션으로 저장
                try {
                    movieSaveService.saveOne(detail);
                    savedCount++;

                } catch (RuntimeException e) {
                    failedCount++;

                    log.warn("영화 저장 실패: page={}, tmdbId={}, reason={}",
                            page, tmdbId, e.getMessage());
                }
            }

            log.info("영화 수집 진행: page={}, 성공={}, 실패={}",
                    page, savedCount, failedCount);

            if (popular.totalPages() != null && page >= popular.totalPages()) {
                break;
            }
        }

        log.info("영화 수집 종료: 요청={}, 성공={}, 실패={}, 중단 페이지={}, 사유={}",
                requestedPages, savedCount, failedCount, stoppedPage, stopReason);

        return new MovieSyncResponse(
                requestedPages,
                savedCount,
                failedCount,
                stoppedPage,
                stopReason
        );
    }

    // TMDB API 예외를 서비스 오류 코드로 변환
    private MovieErrorCode resolveTmdbError(RuntimeException e) {

        if (e instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();

            if (status == 401) {
                return MovieErrorCode.TMDB_AUTH_FAILED;
            }

            if (status == 403) {
                return MovieErrorCode.TMDB_ACCESS_DENIED;
            }
        }

        if (e instanceof ResourceAccessException) {
            return MovieErrorCode.TMDB_CONNECTION_FAILED;
        }

        return MovieErrorCode.TMDB_RESPONSE_FAILED;
    }
}