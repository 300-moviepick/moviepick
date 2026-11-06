package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiMovieCandidateService {

    private static final int MAX_CANDIDATES = 20;
    private static final int SEARCH_POOL_SIZE = 100;

    // AI 랭킹에서 비교할 최대 영화 수
    private static final int RANKING_POOL_SIZE = 5000;

    private final MovieRepository movieRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 기존 일반 후보 조회 API 유지
    public List<AiMovieCandidate> findCandidates(int limit) {

        if (limit < 1 || limit > MAX_CANDIDATES) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        return loadCandidates(limit);
    }

    // 전체 영화 후보를 대상으로 하이브리드 랭킹
    public List<AiMovieCandidate> findCandidatesForRanking() {
        return loadCandidates(RANKING_POOL_SIZE);
    }

    private List<AiMovieCandidate> loadCandidates(int limit) {

        // 일반 조회는 100편 안에서 선택,
        // AI 랭킹은 최대 5000편까지 조회
        int fetchSize = Math.max(
                SEARCH_POOL_SIZE,
                limit
        );

        PageRequest pageable = PageRequest.of(
                0,
                fetchSize,
                Sort.by(
                        Sort.Order.desc("popularity"),
                        Sort.Order.desc("id")
                )
        );

        // ① 활성화된 일반 영화 조회
        List<Movie> movies = movieRepository
                .findByActiveTrueAndAdultFalseAndOverviewIsNotNull(pageable)
                .getContent()
                .stream()
                .filter(movie -> !movie.getOverview().isBlank())
                .limit(limit)
                .toList();

        if (movies.isEmpty()) {
            return List.of();
        }

        // ② 조회된 영화 ID 수집
        List<Long> movieIds = movies.stream()
                .map(Movie::getId)
                .toList();

        // ③ 영화 장르 일괄 조회
        Map<Long, List<String>> genreMap = movieGenreRepository
                .findAllByMovie_IdIn(movieIds)
                .stream()
                .collect(Collectors.groupingBy(
                        genre -> genre.getMovie().getId(),
                        Collectors.mapping(
                                MovieGenre::getGenreName,
                                Collectors.toList()
                        )
                ));

        // ④ AI 추천 후보 DTO 변환
        return movies.stream()
                .map(movie -> AiMovieCandidate.from(
                        movie,
                        genreMap.getOrDefault(
                                movie.getId(),
                                List.of()
                        )
                ))
                .toList();
    }
}