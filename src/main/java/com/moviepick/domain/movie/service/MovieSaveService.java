package com.moviepick.domain.movie.service;

import com.moviepick.domain.movie.client.dto.TmdbMovieResponse;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.model.MovieInfo;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
// 수집한 영화·장르 저장 및 동기화
public class MovieSaveService {

    private static final String KOREA = "KR";

    // TMDB 개봉 종류: 3 극장 개봉, 2 제한 상영 (우선순위 순)
    private static final int[] THEATRICAL_TYPES = {3, 2};

    private final MovieRepository movieRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 영화 1편 단위 저장 및 갱신
    @Transactional
    public Long saveOne(TmdbMovieResponse response) {

        // 1. 필수 데이터 검증
        if (response == null
                || response.id() == null
                || response.title() == null
                || response.title().isBlank()
                || response.adult() == null
                || response.genres() == null) {
            throw new IllegalArgumentException(
                    "TMDB 영화 필수 정보가 없습니다."
            );
        }

        // 2. 기존 영화 조회
        Optional<Movie> existing =
                movieRepository.findByTmdbId(response.id());

        // 3. 기존 영화가 없다면 새 엔티티 생성
        Movie movie = existing.orElseGet(() ->
                new Movie(response.id(), response.title())
        );

        // 4. TMDB 응답을 MovieInfo로 변환
        LocalDate releaseDate =
                response.releaseDate() == null
                        || response.releaseDate().isBlank()
                        ? null
                        : LocalDate.parse(response.releaseDate());

        MovieInfo info = new MovieInfo(
                response.title(),
                response.originalTitle(),
                response.originalLanguage(),
                response.overview(),
                releaseDate,
                response.runtime(),
                response.status(),
                response.adult(),
                response.voteAverage(),
                response.voteCount(),
                response.popularity(),
                response.posterPath(),
                response.backdropPath(),
                findKoreanReleaseDate(response.releaseDates())
        );

        // 5. 영화 정보 갱신
        movie.updateDetails(info);

        // 6. 신규 영화라면 DB에 저장
        if (existing.isEmpty()) {
            movieRepository.save(movie);
        }

        // 7. 영화 장르 동기화
        syncGenres(movie, response.genres());

        // 8. 동기화 성공 시각 기록
        movie.markSynced(LocalDateTime.now());

        return movie.getId();
    }

    // 한국(KR) 극장 개봉일 추출
    // 극장 개봉(3)을 먼저 보고, 없으면 제한 상영(2)을 본다. 같은 종류가 여러 건이면 가장 빠른 날짜.
    // 디지털·TV 공개만 있거나 KR 정보가 없으면 null
    LocalDate findKoreanReleaseDate(TmdbMovieResponse.ReleaseDatesResponse releaseDates) {
        if (releaseDates == null || releaseDates.results() == null) {
            return null;
        }

        List<TmdbMovieResponse.ReleaseDateResponse> krDates = releaseDates.results().stream()
                .filter(country -> country != null && KOREA.equals(country.country()))
                .filter(country -> country.releaseDates() != null)
                .flatMap(country -> country.releaseDates().stream())
                .filter(Objects::nonNull)
                .toList();

        for (int type : THEATRICAL_TYPES) {
            Optional<LocalDate> earliest = krDates.stream()
                    .filter(date -> date.type() != null && date.type() == type)
                    .map(date -> parseReleaseDate(date.releaseDate()))
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder());

            if (earliest.isPresent()) {
                return earliest.get();
            }
        }
        return null;
    }

    // "2026-11-01T00:00:00.000Z" → 2026-11-01 (앞 10자리 날짜만 사용)
    private LocalDate parseReleaseDate(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // 영화 장르 동기화
    private void syncGenres(
            Movie movie,
            List<TmdbMovieResponse.GenreResponse> genres
    ) {

        // 1. 기존 장르 목록 조회
        List<MovieGenre> existingGenres =
                movieGenreRepository.findAllByMovie_Id(movie.getId());

        Map<Integer, MovieGenre> existingMap = new HashMap<>();

        for (MovieGenre genre : existingGenres) {
            existingMap.put(genre.getTmdbGenreId(), genre);
        }

        Set<Integer> receivedIds = new HashSet<>();
        List<MovieGenre> newGenres = new ArrayList<>();

        // 2. TMDB에서 받은 장르 처리
        for (TmdbMovieResponse.GenreResponse genre : genres) {

            if (genre == null || genre.id() == null
                    || genre.name() == null) {
                throw new IllegalArgumentException(
                        "TMDB 장르 정보가 올바르지 않습니다."
                );
            }

            // 응답 내부 중복 장르 방지
            if (!receivedIds.add(genre.id())) {
                continue;
            }

            MovieGenre existingGenre =
                    existingMap.get(genre.id());

            if (existingGenre != null) {
                // 기존 장르 이름 갱신
                existingGenre.updateGenreName(genre.name());
            } else {
                // 신규 장르 생성
                newGenres.add(
                        new MovieGenre(
                                movie,
                                genre.id(),
                                genre.name()
                        )
                );
            }
        }

        // 3. TMDB에서 제거된 장르 삭제
        List<MovieGenre> removedGenres =
                existingGenres.stream()
                        .filter(genre ->
                                !receivedIds.contains(
                                        genre.getTmdbGenreId()
                                )
                        )
                        .toList();

        movieGenreRepository.deleteAll(removedGenres);

        // 4. 새 장르 저장
        movieGenreRepository.saveAll(newGenres);
    }
}
