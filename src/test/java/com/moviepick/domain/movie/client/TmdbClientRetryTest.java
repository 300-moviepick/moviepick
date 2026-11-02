
package com.moviepick.domain.movie.client;

import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TmdbClientRetryTest {

    private static final String BASE_URL =
            "https://api.themoviedb.org/3";

    private static final String POPULAR_URL =
            BASE_URL + "/movie/popular?language=ko-KR&page=1";

    private static final String DETAIL_URL =
            BASE_URL + "/movie/123?language=ko-KR";

    private static final String POPULAR_RESPONSE = """
            {
              "page": 1,
              "results": [],
              "total_pages": 1,
              "total_results": 0
            }
            """;

    private MockRestServiceServer server;
    private TmdbClient tmdbClient;
    private List<Long> retryDelays;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer.bindTo(builder).build();

        retryDelays = new ArrayList<>();

        tmdbClient = new TmdbClient(
                builder.build(),
                millis -> retryDelays.add(millis)
        );
    }

    @Test
    void successWithoutRetry() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        TmdbPopularResponse response =
                tmdbClient.getPopularMovies(1);

        assertEquals(1, response.page());
        assertTrue(response.results().isEmpty());
        assertTrue(retryDelays.isEmpty());
        server.verify();
    }

    @Test
    void retry429ThenSuccess() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        TmdbPopularResponse response =
                tmdbClient.getPopularMovies(1);

        assertEquals(1, response.page());
        assertEquals(List.of(1000L), retryDelays);
        server.verify();
    }

    @Test
    void failAfterThree503Responses() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(POPULAR_URL))
                    .andRespond(withStatus(
                            HttpStatus.SERVICE_UNAVAILABLE
                    ));
        }

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> tmdbClient.getPopularMovies(1)
        );

        assertEquals(503, exception.getStatusCode().value());
        assertEquals(List.of(1000L, 2000L), retryDelays);
        server.verify();
    }

    @Test
    void doNotRetry401() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> tmdbClient.getPopularMovies(1)
        );

        assertEquals(401, exception.getStatusCode().value());
        assertTrue(retryDelays.isEmpty());
        server.verify();
    }

    @Test
    void doNotRetry404() {
        server.expect(requestTo(DETAIL_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> tmdbClient.getMovieDetail(123L)
        );

        assertEquals(404, exception.getStatusCode().value());
        assertTrue(retryDelays.isEmpty());
        server.verify();
    }

    @Test
    void retryNetworkErrorThenSuccess() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(withException(
                        new IOException("Connection reset")
                ));

        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        assertDoesNotThrow(() -> tmdbClient.getPopularMovies(1));

        assertEquals(List.of(1000L), retryDelays);
        server.verify();
    }

    @Test
    void retryAfterSecondsHeader() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.RETRY_AFTER, "5")
                );

        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        TmdbPopularResponse response =
                tmdbClient.getPopularMovies(1);

        assertEquals(1, response.page());
        assertEquals(List.of(5000L), retryDelays);
        server.verify();
    }

    @Test
    void retryAfterHttpDateHeader() {
        String retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME
                .format(
                        ZonedDateTime.now(ZoneOffset.UTC)
                                .plusSeconds(10)
                );

        server.expect(requestTo(POPULAR_URL))
                .andRespond(
                        withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                                .header(HttpHeaders.RETRY_AFTER, retryAfter)
                );

        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        assertDoesNotThrow(() -> tmdbClient.getPopularMovies(1));

        assertEquals(1, retryDelays.size());

        long delay = retryDelays.get(0);

        assertTrue(delay >= 7000L && delay <= 11000L);
        server.verify();
    }

    @Test
    void invalidRetryAfterUsesDefaultDelay() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.RETRY_AFTER, "invalid")
                );

        server.expect(requestTo(POPULAR_URL))
                .andRespond(withSuccess(
                        POPULAR_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        assertDoesNotThrow(() -> tmdbClient.getPopularMovies(1));

        assertEquals(List.of(1000L), retryDelays);
        server.verify();
    }

    @Test
    void stopRetryWhenRetryAfterExceedsLimit() {
        server.expect(requestTo(POPULAR_URL))
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                                .header(HttpHeaders.RETRY_AFTER, "120")
                );

        RestClientResponseException exception = assertThrows(
                RestClientResponseException.class,
                () -> tmdbClient.getPopularMovies(1)
        );

        assertEquals(429, exception.getStatusCode().value());
        assertTrue(retryDelays.isEmpty());
        server.verify();
    }
}
