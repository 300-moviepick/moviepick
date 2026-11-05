package com.moviepick.domain.movie.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moviepick.domain.movie.dto.response.MovieListResponse;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.model.MovieInfo;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 영화 목록 sort=releaseDate(최근 개봉작 순)를 H2로 확인한다.
 * 기준 날짜 = 한국 개봉일, 없으면 원래 개봉일. 오늘은 실행 시점과 무관하게 TODAY로 고정한다.
 * 저장 순서(id)와 날짜 순서를 일부러 다르게 넣어 id 정렬과 구분한다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, MovieQueryService.class})
class MovieQueryServiceSortTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private MovieQueryService movieQueryService;
    @Autowired
    private MovieRepository movieRepository;

    private long tmdbIdSequence = 1L;

    @Test
    @DisplayName("sort 없음: 기존과 같이 id 내림차순이고 개봉일·미개봉 여부와 무관하다")
    void defaultSortIsIdDesc() {
        Long first = save("first", LocalDate.of(2026, 11, 1), null);
        Long second = save("second", null, null);
        Long third = save("third", LocalDate.of(2027, 1, 1), null);

        Page<MovieListResponse> result = movieQueryService.getMovies(
                null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(ids(result)).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("한국 개봉일이 있으면 원래 개봉일보다 한국 개봉일을 기준으로 정렬한다")
    void koreanReleaseDateFirst() {
        // 원래 개봉일만 보면 krLater가 먼저지만, 한국 개봉일 기준이면 noKr(10-15)가 먼저다
        Long noKr = save("noKr", LocalDate.of(2026, 10, 15), null);
        Long krEarlier = save("krEarlier", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 1));

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(noKr, krEarlier);
    }

    @Test
    @DisplayName("한국 개봉일이 없으면 원래 개봉일 기준으로 정렬한다")
    void releaseDateFallback() {
        Long newer = save("newer", LocalDate.of(2026, 11, 1), null);
        Long older = save("older", LocalDate.of(2026, 10, 1), null);

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(newer, older);
    }

    @Test
    @DisplayName("한국 개봉일과 원래 개봉일이 모두 없는 영화는 제외한다")
    void noDateExcluded() {
        save("noDate", null, null);
        Long dated = save("dated", LocalDate.of(2026, 10, 1), null);

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(dated);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("기준 날짜가 오늘보다 뒤면(미개봉) 제외하고, 오늘 개봉은 포함한다")
    void unreleasedExcludedTodayIncluded() {
        Long today = save("today", null, TODAY);
        save("tomorrow", TODAY.plusDays(1), null);
        // 원래 개봉일은 지났어도 한국 개봉일이 내일이면 미개봉이다
        save("krTomorrow", LocalDate.of(2026, 10, 1), TODAY.plusDays(1));

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(today);
    }

    @Test
    @DisplayName("기준 날짜가 같으면 id 내림차순")
    void sameDateIdDesc() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        Long first = save("first", date, null);
        Long second = save("second", null, date);
        Long third = save("third", LocalDate.of(2026, 1, 1), date);

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("active = false 영화는 제외한다")
    void inactiveExcluded() {
        Long active = save("active", LocalDate.of(2026, 10, 1), null);
        Long inactive = save("inactive", LocalDate.of(2026, 11, 1), null);
        Movie movie = movieRepository.findById(inactive).orElseThrow();
        ReflectionTestUtils.setField(movie, "active", false);
        movieRepository.flush();

        Page<MovieListResponse> result = recent(null, FIRST_PAGE);

        assertThat(ids(result)).containsExactly(active);
    }

    @Test
    @DisplayName("keyword와 함께 쓰면 제목 검색(대소문자 무시, 앞뒤 공백 제거)과 개봉일 조건이 모두 적용된다")
    void keywordAndReleaseDate() {
        Long matched = save("Avengers Origin", LocalDate.of(2026, 10, 1), null);
        save("Avengers Future", TODAY.plusDays(10), null);
        save("Other Movie", LocalDate.of(2026, 11, 1), null);

        Page<MovieListResponse> result = recent("  aveNGers ", FIRST_PAGE);

        assertThat(ids(result)).containsExactly(matched);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("totalElements·totalPages는 필터된 개수 기준이다")
    void totalElementsMatchesFilter() {
        for (int day = 1; day <= 5; day++) {
            save("released" + day, LocalDate.of(2026, 10, day), null);
        }
        save("unreleased", TODAY.plusDays(1), null);
        save("noDate", null, null);

        Page<MovieListResponse> firstPage = recent(null, PageRequest.of(0, 2));
        Page<MovieListResponse> lastPage = recent(null, PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(lastPage.getContent()).extracting(MovieListResponse::title)
                .containsExactly("released1");
    }

    @Test
    @DisplayName("응답에 releaseDateKr이 담기고, 한국 개봉일이 없으면 null이다")
    void responseHasReleaseDateKr() {
        save("noKr", LocalDate.of(2026, 10, 1), null);
        save("kr", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 10));

        List<MovieListResponse> content = recent(null, FIRST_PAGE).getContent();

        assertThat(content.get(0).title()).isEqualTo("kr");
        assertThat(content.get(0).releaseDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(content.get(0).releaseDateKr()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(content.get(1).title()).isEqualTo("noKr");
        assertThat(content.get(1).releaseDateKr()).isNull();
    }

    private Page<MovieListResponse> recent(String keyword, PageRequest pageable) {
        return movieQueryService.getRecentlyReleasedMovies(keyword, pageable, TODAY);
    }

    private Long save(String title, LocalDate releaseDate, LocalDate releaseDateKr) {
        Movie movie = new Movie(tmdbIdSequence++, title);
        movie.updateDetails(new MovieInfo(title, null, null, null, releaseDate, null, null,
                false, null, null, null, null, null, releaseDateKr));
        return movieRepository.saveAndFlush(movie).getId();
    }

    private static List<Long> ids(Page<MovieListResponse> page) {
        return page.getContent().stream().map(MovieListResponse::movieId).toList();
    }
}
