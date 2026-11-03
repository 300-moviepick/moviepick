package com.moviepick.domain.movie.client;

import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.client.dto.TmdbPopularResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

    private static final String POPULAR_RESPONSE = """
            {
              "page": 1,
              "results": [],
              "total_pages": 1,
              "total_results": 0
            }
            """;

    private static final String DETAIL_RESPONSE = """
            {
              "id": 550,
              "title": "테스트 영화",
              "genres": [
                {
                  "id": 18,
                  "name": "드라마"
                }
              ],
              "videos": {
                "results": [
                  {
                    "id": "video1",
                    "name": "Trailer",
                    "key": "abc123",
                    "site": "YouTube",
                    "type": "Trailer",
                    "official": true,
                    "iso_639_1": "en"
                  }
                ]
              },
              "credits": {
                "cast": [
                  {
                    "id": 101,
                    "name": "테스트 배우",
                    "character": "주인공",
                    "order": 0,
                    "profile_path": "/actor.jpg"
                  }
                ],
                "crew": [
                  {
                    "id": 201,
                    "name": "테스트 감독",
                    "job": "Director",
                    "department": "Directing"
                  }
                ]
              },
              "watch/providers": {
                "results": {
                  "KR": {
                    "link": "https://www.themoviedb.org/movie/550/watch",
                    "flatrate": [
                      {
                        "provider_id": 8,
                        "provider_name": "테스트 OTT",
                        "logo_path": "/ott.png"
                      }
                    ]
                  }
                }
              },
              "release_dates": {
                "results": [
                  {
                    "iso_3166_1": "KR",
                    "release_dates": [
                      {
                        "release_date": "2026-11-01T00:00:00.000Z",
                        "type": 3
                      }
                    ]
                  }
                ]
              }
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
        server.expect(detailRequest(123L))
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

    @Test
    void deserializeAppendedMovieDetails() {
        server.expect(detailRequest(550L))
                .andRespond(withSuccess(
                        DETAIL_RESPONSE,
                        MediaType.APPLICATION_JSON
                ));

        TmdbMovieResponse response =
                tmdbClient.getMovieDetail(550L);

        assertEquals(550L, response.id());
        assertEquals("테스트 영화", response.title());

        // 예고편
        assertNotNull(response.videos());
        assertEquals(
                "abc123",
                response.videos().results().get(0).key()
        );
        assertEquals(
                "YouTube",
                response.videos().results().get(0).site()
        );

        // 출연진 및 감독
        assertNotNull(response.credits());
        assertEquals(
                "테스트 배우",
                response.credits().cast().get(0).name()
        );
        assertEquals(
                "Director",
                response.credits().crew().get(0).job()
        );

        // 한국 OTT
        assertNotNull(response.watchProviders());

        var koreanProviders =
                response.watchProviders().results().get("KR");

        assertNotNull(koreanProviders);
        assertEquals(
                "테스트 OTT",
                koreanProviders.flatrate().get(0).providerName()
        );

        // 국가별 개봉일
        assertNotNull(response.releaseDates());

        var koreanRelease = response.releaseDates().results().get(0);

        assertEquals("KR", koreanRelease.country());
        assertEquals(
                "2026-11-01T00:00:00.000Z",
                koreanRelease.releaseDates().get(0).releaseDate()
        );
        assertEquals(3, koreanRelease.releaseDates().get(0).type());

        assertTrue(retryDelays.isEmpty());
        server.verify();
    }

    private RequestMatcher detailRequest(long tmdbId) {
        return request -> {
            var uri = request.getURI();

            assertEquals(
                    "/3/movie/" + tmdbId,
                    uri.getPath()
            );

            String query = URLDecoder.decode(
                    uri.getRawQuery(),
                    StandardCharsets.UTF_8
            );

            assertTrue(query.contains("language=ko-KR"));

            assertTrue(query.contains(
                    "append_to_response=videos,credits,watch/providers,release_dates"
            ));
        };
    }
}
