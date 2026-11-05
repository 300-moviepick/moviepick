package com.moviepick.domain.movie.entity;

import com.moviepick.domain.movie.model.MovieInfo;
import com.moviepick.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movies")
public class Movie extends BaseEntity {

    // TMDB 이미지 서버 주소 (posterPath 앞에 붙여 전체 URL을 만든다)
    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    // 내부 영화 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TMDB 영화 고유 ID
    @Column(name = "tmdb_id", nullable = false, unique = true)
    private Long tmdbId;

    // 영화 제목
    @Column(nullable = false, length = 500)
    private String title;

    // 영화 원제
    @Column(name = "original_title", length = 500)
    private String originalTitle;

    // 원어 코드
    @Column(name = "original_language", length = 10)
    private String originalLanguage;

    // 영화 줄거리
    @Column(columnDefinition = "TEXT")
    private String overview;

    // 개봉일
    @Column(name = "release_date")
    private LocalDate releaseDate;

    // 한국 극장 개봉일 (TMDB release_dates의 KR, 없으면 null)
    @Column(name = "release_date_kr")
    private LocalDate releaseDateKr;

    // 상영 시간 (분)
    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    // 영화 상태
    @Column(length = 50)
    private String status;

    // 성인 영화 여부
    @Column(nullable = false)
    private boolean adult;

    // TMDB 평균 평점
    @Column(name = "vote_average")
    private Double voteAverage;

    // TMDB 평가 참여자 수
    @Column(name = "vote_count")
    private Integer voteCount;

    // TMDB 인기도
    private Double popularity;

    // 포스터 이미지 경로
    @Column(name = "poster_path")
    private String posterPath;

    // 배경 이미지 경로
    @Column(name = "backdrop_path")
    private String backdropPath;

    // 서비스 노출 여부
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // 마지막 TMDB 동기화 성공 시각
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // 우리 서비스 리뷰 평점 합계
    @Column(nullable = false, precision = 12, scale = 1)
    private BigDecimal ratingSum = BigDecimal.ZERO;

    // 우리 서비스 리뷰 개수
    @Column(nullable = false)
    private int reviewCount;

    // 최초 영화 생성
    public Movie(Long tmdbId, String title) {
        this.tmdbId = tmdbId;
        this.title = title;
    }

    // TMDB 동기화 완료 시각 기록
    public void markSynced(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
    }

    // 영화 기본 정보 갱신
    public void updateDetails(MovieInfo info) {
        this.title = info.title();
        this.originalTitle = info.originalTitle();
        this.originalLanguage = info.originalLanguage();
        this.overview = info.overview();
        this.releaseDate = info.releaseDate();
        this.releaseDateKr = info.releaseDateKr();
        this.runtimeMinutes = info.runtimeMinutes();
        this.status = info.status();
        this.adult = info.adult();
        this.voteAverage = info.voteAverage();
        this.voteCount = info.voteCount();
        this.popularity = info.popularity();
        this.posterPath = info.posterPath();
        this.backdropPath = info.backdropPath();
    }

    /** 평점 합계 ÷ 리뷰 수 (소수 첫째 자리 반올림, 리뷰가 없으면 0) */
    public BigDecimal getAvgRating() {
        // 리뷰가 하나도 없다면 0 반환
        if (reviewCount == 0) {
            return BigDecimal.ZERO;
        }

        // 평균 평점 계산
        return ratingSum.divide(
                BigDecimal.valueOf(reviewCount),
                1,
                RoundingMode.HALF_UP
        );
    }
    
    /** 포스터 전체 URL (포스터가 없으면 null) */
    public String getPosterUrl() {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        return TMDB_IMAGE_BASE_URL + posterPath;
    }
}
