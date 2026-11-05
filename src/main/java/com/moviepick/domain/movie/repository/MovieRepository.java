package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.entity.Movie;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    // TMDB 영화 ID로 조회 (중복 확인 및 동기화)
    Optional<Movie> findByTmdbId(Long tmdbId);

    // 서비스에 노출할 영화 목록 조회
    Page<Movie> findAllByActiveTrue(Pageable pageable);

    // 서비스에 노출할 영화 상세 조회
    Optional<Movie> findByIdAndActiveTrue(Long id);

    // 영화 제목 검색
    Page<Movie> findByActiveTrueAndTitleContainingIgnoreCase(
            String title,
            Pageable pageable
    );

    // 리뷰 작성 시 같은 영화의 작성을 한 줄로 세우기 위한 쓰기 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Movie m where m.id = :id")
    Optional<Movie> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("""
    UPDATE Movie m
    SET m.ratingSum = m.ratingSum + :ratingDelta,
        m.reviewCount = m.reviewCount + :countDelta,
        m.updatedAt = CURRENT_TIMESTAMP
    WHERE m.id = :movieId
      AND m.reviewCount + :countDelta >= 0
      AND m.ratingSum + :ratingDelta >= 0
    """)
    int applyRating(
            @Param("movieId") Long movieId,
            @Param("ratingDelta") BigDecimal ratingDelta,
            @Param("countDelta") int countDelta
    );

    // [추가] AI 추천 후보용 영화 조회
    Page<Movie> findByActiveTrueAndAdultFalseAndOverviewIsNotNull(
            Pageable pageable
    );
}
