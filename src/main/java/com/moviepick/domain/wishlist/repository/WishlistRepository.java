package com.moviepick.domain.wishlist.repository;

import com.moviepick.domain.member.entity.MemberStatus;
import com.moviepick.domain.wishlist.entity.Wishlist;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    boolean existsByMemberIdAndMovieId(Long memberId, Long movieId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Wishlist w where w.member.id = :memberId and w.movie.id = :movieId")
    int deleteByMemberIdAndMovieId(@Param("memberId") Long memberId, @Param("movieId") Long movieId);

    // 내 찜 목록: 활성 영화만 (N+1 방지: EntityGraph)
    @EntityGraph(attributePaths = "movie")
    Page<Wishlist> findByMemberIdAndMovieActiveTrue(Long memberId, Pageable pageable);

    // 개봉 알림 대상: 해당 날짜에 개봉하는 활성 영화를 찜한 회원 (제외 상태 회원 빼고)
    // 개봉일은 한국 개봉일(releaseDateKr)을 쓰고, 없으면 TMDB 기본 개봉일(releaseDate)을 쓴다
    @Query("""
            select w from Wishlist w
            join fetch w.member m
            join fetch w.movie mv
            where mv.active = true
              and coalesce(mv.releaseDateKr, mv.releaseDate) = :releaseDate
              and m.status <> :excludedStatus
            """)
    List<Wishlist> findReleaseTargets(
            @Param("releaseDate") LocalDate releaseDate,
            @Param("excludedStatus") MemberStatus excludedStatus
    );
}
