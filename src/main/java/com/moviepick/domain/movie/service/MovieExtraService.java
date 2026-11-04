package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.client.TmdbClient;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.dto.response.MovieExtraResponse;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.exception.MovieErrorCode;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
public class MovieExtraService {

    private final MovieRepository movieRepository;
    private final TmdbClient tmdbClient;

    @Cacheable(cacheNames = "movieExtras", key = "#movieId")
    public MovieExtraResponse getExtras(Long movieId) {

        // MoviePick 내부 영화 ID로 조회
        Movie movie = movieRepository.findByIdAndActiveTrue(movieId)
                .orElseThrow(() ->
                        new BusinessException(MovieErrorCode.MOVIE_NOT_FOUND)
                );

        try {
            // TMDB ID를 사용해 확장 데이터 조회
            TmdbMovieResponse response =
                    tmdbClient.getMovieDetail(movie.getTmdbId());

            return MovieExtraResponse.from(movieId, response);

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();

            if (status == 401) {
                throw new BusinessException(
                        MovieErrorCode.TMDB_AUTH_FAILED
                );
            }

            if (status == 403) {
                throw new BusinessException(
                        MovieErrorCode.TMDB_ACCESS_DENIED
                );
            }

            throw new BusinessException(
                    MovieErrorCode.TMDB_RESPONSE_FAILED
            );

        } catch (ResourceAccessException e) {
            throw new BusinessException(
                    MovieErrorCode.TMDB_CONNECTION_FAILED
            );
        }
    }
}
