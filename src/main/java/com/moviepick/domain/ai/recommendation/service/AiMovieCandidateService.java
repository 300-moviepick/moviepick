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

    private final MovieRepository movieRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 기존: 직접 지정한 개수만큼 후보 반환
    public List<AiMovieCandidate> findCandidates(int limit) {

        if (limit < 1 || limit > MAX_CANDIDATES) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        return loadCandidates(limit);
    }

    // [추가 ①] 랭킹에 사용할 후보 풀 조회
    public List<AiMovieCandidate> findCandidatesForRanking() {
        return loadCandidates(SEARCH_POOL_SIZE);
    }

    // [변경 ②] 기존 조회 로직을 공통 메서드로 분리
    private List<AiMovieCandidate> loadCandidates(int limit) {

        PageRequest pageable = PageRequest.of(
                0,
                SEARCH_POOL_SIZE,
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

        // ② 후보 영화 ID 추출
        List<Long> movieIds = movies.stream()
                .map(Movie::getId)
                .toList();

        // ③ 장르 일괄 조회
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