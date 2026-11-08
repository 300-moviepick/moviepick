package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.dto.response.MovieRankingResponse;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.QMovie;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MovieRankingRepositoryImpl implements MovieRankingRepository {

    private static final int BENCHMARK_REVIEW_COUNT = 10;

    private final JPAQueryFactory queryFactory;

    private final QMovie movie = QMovie.movie;

    @Override
    public Page<MovieRankingResponse> findRanking(Pageable pageable) {

        // 1. 랭킹 대상 영화 조건: 활성화되어 있고 리뷰가 1개 이상인 영화
        var condition = movie.active.isTrue()
                .and(movie.reviewCount.gt(0));

        // 2. 전체 대상 영화의 평점 합계와 리뷰 개수 집계
        NumberExpression<BigDecimal> totalRatingSumExpression =
                movie.ratingSum.sumBigDecimal();

        NumberExpression<Long> totalReviewCountExpression =
                movie.reviewCount.sumLong();

        Tuple aggregate = queryFactory
                .select(totalRatingSumExpression, totalReviewCountExpression)
                .from(movie)
                .where(condition)
                .fetchOne();

        BigDecimal totalRatingSum = aggregate == null
                ? BigDecimal.ZERO
                : Optional.ofNullable(aggregate.get(totalRatingSumExpression))
                        .orElse(BigDecimal.ZERO);

        Long totalReviewCount = aggregate == null
                ? 0L
                : aggregate.get(totalReviewCountExpression);

        // 3. 랭킹 대상 영화가 없으면 빈 페이지 반환
        if (totalReviewCount == null || totalReviewCount == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 4. 전체 리뷰의 가중 평균 평점 계산
        double globalAverage = totalRatingSum.doubleValue()
                / totalReviewCount;

        // 5. 영화별 랭킹 점수 계산
        NumberExpression<Double> reviewCount =
                movie.reviewCount.doubleValue();

        NumberExpression<Double> ratingSum =
                movie.ratingSum.doubleValue();

        NumberExpression<Double> rankingScore =
                Expressions.numberTemplate(
                        Double.class,
                        "({0} / ({0} + {1})) * ({2} / {0})"
                                + " + ({1} / ({0} + {1})) * {3}",
                        reviewCount,
                        Expressions.constant((double) BENCHMARK_REVIEW_COUNT),
                        ratingSum,
                        Expressions.constant(globalAverage)
                );

        // 6. 전체 랭킹 대상 영화 개수 조회
        Long totalElements = queryFactory
                .select(movie.count())
                .from(movie)
                .where(condition)
                .fetchOne();

        // 7. 점수 내림차순 정렬 후 DB에서 페이징
        List<Tuple> results = queryFactory
                .select(movie, rankingScore)
                .from(movie)
                .where(condition)
                .orderBy(rankingScore.desc(), movie.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 8. 응답 DTO 변환
        List<MovieRankingResponse> content = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {

            Tuple tuple = results.get(i);

            Movie rankedMovie = tuple.get(movie);
            Double score = tuple.get(rankingScore);

            int rank = Math.toIntExact(pageable.getOffset() + i + 1);

            content.add(new MovieRankingResponse(
                    rank,
                    rankedMovie.getId(),
                    rankedMovie.getTitle(),
                    rankedMovie.getPosterUrl(),
                    rankedMovie.getReleaseDate(),
                    rankedMovie.getAvgRating(),
                    rankedMovie.getReviewCount(),
                    BigDecimal.valueOf(score)
                            .setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return new PageImpl<>(
                content,
                pageable,
                totalElements == null ? 0 : totalElements
        );
    }
}