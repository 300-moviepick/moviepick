package com.moviepick.domain.movie.repository;

import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.QMovie;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.DateExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

// MovieRepository의 사용자 정의 구현(fragment).
// JPA 슬라이스 테스트(@DataJpaTest)에서도 MovieRepository와 함께 만들어지도록 EntityManager로 JPAQueryFactory를 직접 생성한다
public class MovieReleaseRepositoryImpl implements MovieReleaseRepository {

    private final JPAQueryFactory queryFactory;

    private final QMovie movie = QMovie.movie;

    public MovieReleaseRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<Movie> findReleased(String keyword, LocalDate today, Pageable pageable) {

        // 1. 기준 날짜: 한국 개봉일이 있으면 그 값, 없으면 원래 개봉일
        DateExpression<LocalDate> baseDate = movie.releaseDateKr.coalesce(movie.releaseDate);

        // 2. 조건: 활성 영화 + (제목 검색) + 기준 날짜가 있고 오늘 이하(미개봉 제외)
        BooleanBuilder condition = new BooleanBuilder()
                .and(movie.active.isTrue())
                .and(baseDate.isNotNull())
                .and(baseDate.loe(today));

        if (keyword != null) {
            condition.and(movie.title.containsIgnoreCase(keyword));
        }

        // 3. 기준 날짜 내림차순, 같으면 id 내림차순으로 DB에서 페이징
        List<Movie> content = queryFactory
                .selectFrom(movie)
                .where(condition)
                .orderBy(baseDate.desc(), movie.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 4. 같은 조건으로 전체 개수 조회 (totalElements가 필터 결과와 맞도록)
        Long total = queryFactory
                .select(movie.count())
                .from(movie)
                .where(condition)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }
}
