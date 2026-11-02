package com.moviepick.domain.movie.client;

import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

@Slf4j
@Component
public class TmdbClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 1000L;
    private static final long MAX_RETRY_AFTER_MILLIS = 30_000L;

    private final RestClient restClient;
    private final RetrySleeper retrySleeper;

    @Autowired
    public TmdbClient(@Value("${tmdb.access-token}") String token) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.themoviedb.org/3")
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + token
                )
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();

        this.retrySleeper = Thread::sleep;
    }

    TmdbClient(RestClient restClient) {
        this(restClient, Thread::sleep);
    }

    TmdbClient(RestClient restClient, RetrySleeper retrySleeper) {
        this.restClient = restClient;
        this.retrySleeper = retrySleeper;
    }

    public TmdbPopularResponse getPopularMovies(int page) {
        if (page < 1) {
            throw new IllegalArgumentException(
                    "페이지 번호는 1 이상이어야 합니다."
            );
        }

        TmdbPopularResponse response = executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/movie/popular")
                                .queryParam("language", "ko-KR")
                                .queryParam("page", page)
                                .build())
                        .retrieve()
                        .body(TmdbPopularResponse.class)
        );

        if (response == null || response.results() == null) {
            throw new IllegalStateException(
                    "TMDB 인기 영화 응답이 비어 있습니다."
            );
        }

        return response;
    }

    public TmdbMovieResponse getMovieDetail(Long tmdbId) {
        if (tmdbId == null || tmdbId <= 0) {
            throw new IllegalArgumentException(
                    "올바른 TMDB 영화 ID가 필요합니다."
            );
        }

        TmdbMovieResponse response = executeWithRetry(() ->
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/movie/{movieId}")
                                .queryParam("language", "ko-KR")
                                .build(tmdbId))
                        .retrieve()
                        .body(TmdbMovieResponse.class)
        );

        if (response == null) {
            throw new IllegalStateException(
                    "TMDB 영화 상세 응답이 비어 있습니다."
            );
        }

        return response;
    }

    private <T> T executeWithRetry(Supplier<T> apiCall) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return apiCall.get();

            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();

                if (!isRetryableStatus(status)
                        || attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                long delay = resolveRetryDelay(e, attempt);

                // 서버가 요구한 대기 시간이 너무 길면 재시도 중단
                if (delay < 0) {
                    log.warn(
                            "TMDB API 재시도 중단: Retry-After가 30초를 초과합니다."
                    );
                    throw e;
                }

                waitBeforeRetry(
                        attempt,
                        "HTTP " + status,
                        delay
                );

            } catch (ResourceAccessException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }

                waitBeforeRetry(
                        attempt,
                        "네트워크 오류",
                        RETRY_DELAY_MILLIS * attempt
                );
            }
        }

        throw new IllegalStateException(
                "TMDB API 재시도 처리 중 예기치 않은 오류가 발생했습니다."
        );
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    private long resolveRetryDelay(
            RestClientResponseException exception,
            int attempt
    ) {
        long defaultDelay = RETRY_DELAY_MILLIS * attempt;

        HttpHeaders headers = exception.getResponseHeaders();

        if (headers == null) {
            return defaultDelay;
        }

        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);

        if (retryAfter == null || retryAfter.isBlank()) {
            return defaultDelay;
        }

        retryAfter = retryAfter.trim();

        // 초 단위 Retry-After 처리
        if (retryAfter.matches("[0-9]+")) {
            try {
                long seconds = Long.parseLong(retryAfter);

                if (seconds > MAX_RETRY_AFTER_MILLIS / 1000) {
                    return -1;
                }

                return seconds * 1000;
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        // HTTP 날짜 형식 Retry-After 처리
        try {
            Instant retryTime = ZonedDateTime.parse(
                    retryAfter,
                    DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant();

            long delay = Math.max(
                    0,
                    Duration.between(Instant.now(), retryTime).toMillis()
            );

            return delay > MAX_RETRY_AFTER_MILLIS ? -1 : delay;

        } catch (DateTimeParseException e) {
            log.warn(
                    "잘못된 Retry-After 헤더: {}. 기본 대기 시간을 적용합니다.",
                    retryAfter
            );
            return defaultDelay;
        }
    }

    private void waitBeforeRetry(
            int attempt,
            String reason,
            long delay
    ) {
        log.warn(
                "TMDB API 재시도: 원인={}, 현재 시도={}/{}, 대기={}ms",
                reason,
                attempt,
                MAX_ATTEMPTS,
                delay
        );

        try {
            retrySleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "TMDB API 재시도 대기가 중단됐습니다.",
                    e
            );
        }
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
