package com.moviepick.domain.movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.movie.client.dto.TmdbMovieResponse.CountryReleaseDatesResponse;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse.ReleaseDateResponse;
import com.moviepick.domain.movie.client.dto.TmdbMovieResponse.ReleaseDatesResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MovieSaveServiceReleaseDateTest {

    // 한국 개봉일 추출은 저장소를 쓰지 않는다
    private final MovieSaveService movieSaveService = new MovieSaveService(null, null);

    @Test
    @DisplayName("KR 극장 개봉일(type 3)을 고르고, 다른 나라 개봉일은 무시한다")
    void theatricalReleaseInKorea() {
        ReleaseDatesResponse releaseDates = new ReleaseDatesResponse(List.of(
                country("US", date("2026-10-20T00:00:00.000Z", 3)),
                country("KR",
                        date("2026-10-25T00:00:00.000Z", 1),
                        date("2026-11-01T00:00:00.000Z", 3))));

        assertThat(movieSaveService.findKoreanReleaseDate(releaseDates))
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    @DisplayName("극장 개봉이 여러 건이면 가장 빠른 날짜, 극장 개봉이 없으면 제한 상영(type 2)")
    void earliestAndLimitedFallback() {
        ReleaseDatesResponse twoTheatrical = new ReleaseDatesResponse(List.of(
                country("KR",
                        date("2026-11-08T00:00:00.000Z", 3),
                        date("2026-11-01T00:00:00.000Z", 3))));
        ReleaseDatesResponse limitedOnly = new ReleaseDatesResponse(List.of(
                country("KR",
                        date("2026-12-01T00:00:00.000Z", 4),
                        date("2026-11-15T00:00:00.000Z", 2))));

        assertThat(movieSaveService.findKoreanReleaseDate(twoTheatrical))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(movieSaveService.findKoreanReleaseDate(limitedOnly))
                .isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    @DisplayName("KR 정보가 없거나 디지털 공개만 있거나 날짜가 잘못되면 null")
    void noKoreanTheatricalRelease() {
        ReleaseDatesResponse noKorea = new ReleaseDatesResponse(List.of(
                country("US", date("2026-10-20T00:00:00.000Z", 3))));
        ReleaseDatesResponse digitalOnly = new ReleaseDatesResponse(List.of(
                country("KR", date("2026-12-01T00:00:00.000Z", 4))));
        ReleaseDatesResponse invalidDate = new ReleaseDatesResponse(List.of(
                country("KR", date("not-a-date", 3))));

        assertThat(movieSaveService.findKoreanReleaseDate(null)).isNull();
        assertThat(movieSaveService.findKoreanReleaseDate(noKorea)).isNull();
        assertThat(movieSaveService.findKoreanReleaseDate(digitalOnly)).isNull();
        assertThat(movieSaveService.findKoreanReleaseDate(invalidDate)).isNull();
    }

    private static CountryReleaseDatesResponse country(String code, ReleaseDateResponse... dates) {
        return new CountryReleaseDatesResponse(code, List.of(dates));
    }

    private static ReleaseDateResponse date(String releaseDate, int type) {
        return new ReleaseDateResponse(releaseDate, type);
    }
}
