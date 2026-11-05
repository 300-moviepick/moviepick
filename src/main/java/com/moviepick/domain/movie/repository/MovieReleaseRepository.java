package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.entity.Movie;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MovieReleaseRepository {

    /**
     * 기준 날짜(한국 개봉일, 없으면 원래 개봉일)가 today 이하인 활성 영화를 최근 개봉순으로 조회한다.
     * keyword가 null이면 제목 조건을 붙이지 않는다. 정렬은 pageable의 Sort를 쓰지 않는다.
     */
    Page<Movie> findReleased(String keyword, LocalDate today, Pageable pageable);
}
