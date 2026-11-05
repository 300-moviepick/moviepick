package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.entity.MovieGenre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MovieGenreRepository extends JpaRepository<MovieGenre, Long> {

    // 특정 영화의 장르 목록 조회
    List<MovieGenre> findAllByMovie_Id(Long movieId);

    // [추가] 여러 영화의 장르를 한 번에 조회
    List<MovieGenre> findAllByMovie_IdIn(
            Collection<Long> movieIds
    );
}
