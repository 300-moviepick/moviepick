package com.moviepick.domain.ai.recommendation.repository;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AiMovieEmbeddingBatchReader {

    private static final int MAX_PAGE_SIZE = 100;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // 마지막으로 조회한 영화 ID 이후의 영화 조회
    public List<AiMovieCandidate> findNext(
            long afterMovieId,
            int pageSize
    ) {
        if (afterMovieId < 0
                || pageSize < 1
                || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "조회 조건이 올바르지 않습니다."
            );
        }

        String movieSql = """
                SELECT
                    id,
                    tmdb_id,
                    title,
                    overview,
                    release_date,
                    vote_average,
                    popularity
                FROM movies
                WHERE id > :afterMovieId
                  AND is_active = 1
                  AND adult = 0
                  AND overview IS NOT NULL
                  AND TRIM(overview) <> ''
                ORDER BY id ASC
                LIMIT :pageSize
                """;

        Map<String, Object> params = Map.of(
                "afterMovieId", afterMovieId,
                "pageSize", pageSize
        );

        List<MovieRow> movies = jdbcTemplate.query(
                movieSql,
                params,
                (rs, rowNum) -> new MovieRow(
                        rs.getLong("id"),
                        rs.getLong("tmdb_id"),
                        rs.getString("title"),
                        rs.getString("overview"),
                        rs.getObject(
                                "release_date",
                                LocalDate.class
                        ),
                        rs.getObject(
                                "vote_average",
                                Double.class
                        ),
                        rs.getObject(
                                "popularity",
                                Double.class
                        )
                )
        );

        if (movies.isEmpty()) {
            return List.of();
        }

        // 영화 ID 목록
        List<Long> movieIds = movies.stream()
                .map(MovieRow::id)
                .toList();

        // 장르 N+1 방지: 한 번에 조회
        String genreSql = """
                SELECT
                    movie_id,
                    genre_name
                FROM movie_genres
                WHERE movie_id IN (:movieIds)
                ORDER BY movie_id, tmdb_genre_id
                """;

        List<GenreRow> genres = jdbcTemplate.query(
                genreSql,
                Map.of("movieIds", movieIds),
                (rs, rowNum) -> new GenreRow(
                        rs.getLong("movie_id"),
                        rs.getString("genre_name")
                )
        );

        Map<Long, List<String>> genreMap = new HashMap<>();

        for (GenreRow genre : genres) {
            genreMap.computeIfAbsent(
                    genre.movieId(),
                    id -> new ArrayList<>()
            ).add(genre.genreName());
        }

        return movies.stream()
                .map(movie -> new AiMovieCandidate(
                        movie.id(),
                        movie.tmdbId(),
                        movie.title(),
                        movie.overview(),
                        movie.releaseDate(),
                        List.copyOf(genreMap.getOrDefault(
                                movie.id(),
                                List.of()
                        )),
                        movie.voteAverage(),
                        movie.popularity(),
                        null
                ))
                .toList();
    }

    private record MovieRow(
            Long id,
            Long tmdbId,
            String title,
            String overview,
            LocalDate releaseDate,
            Double voteAverage,
            Double popularity
    ) {
    }

    private record GenreRow(
            Long movieId,
            String genreName
    ) {
    }
}