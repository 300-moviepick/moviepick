package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.domain.review.entity.Review;
import com.moviepick.domain.review.entity.ReviewStatus;
import com.moviepick.domain.review.repository.ReviewRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiMemberGenrePreferenceService {

    private static final int REVIEW_LIMIT = 100;

    private final ReviewRepository reviewRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 회원 리뷰를 분석해 장르별 선호 점수 계산
    public Map<String, Double> getGenreWeights(Long memberId) {

        if (memberId == null || memberId <= 0) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        // ① 공개 리뷰만 최근 100건 조회
        List<Review> reviews = reviewRepository
                .findByMemberIdAndStatusIn(
                        memberId,
                        List.of(ReviewStatus.VISIBLE),
                        PageRequest.of(
                                0,
                                REVIEW_LIMIT,
                                Sort.by(
                                        Sort.Order.desc("updatedAt"),
                                        Sort.Order.desc("id")
                                )
                        )
                )
                .getContent();

        if (reviews.isEmpty()) {
            return Map.of();
        }

        // ② 평가한 영화 ID 추출
        List<Long> movieIds = reviews.stream()
                .map(review -> review.getMovie().getId())
                .distinct()
                .toList();

        // ③ 영화별 장르 일괄 조회
        Map<Long, List<String>> genreMap =
                movieGenreRepository.findAllByMovie_IdIn(movieIds)
                        .stream()
                        .filter(genre ->
                                genre.getGenreName() != null
                                        && !genre.getGenreName().isBlank()
                        )
                        .collect(Collectors.groupingBy(
                                genre -> genre.getMovie().getId(),
                                Collectors.mapping(
                                        genre -> normalize(genre.getGenreName()),
                                        Collectors.toList()
                                )
                        ));

        // ④ 장르별 평점 점수 집계
        Map<String, DoubleSummaryStatistics> statistics =
                new HashMap<>();

        for (Review review : reviews) {

            double weight =
                    (review.getRating().doubleValue() - 3.0) * 4.0;

            List<String> genres = genreMap.getOrDefault(
                    review.getMovie().getId(),
                    List.of()
            );

            for (String genre : genres.stream().distinct().toList()) {
                statistics
                        .computeIfAbsent(
                                genre,
                                key -> new DoubleSummaryStatistics()
                        )
                        .accept(weight);
            }
        }

        // ⑤ 장르별 평균 선호 점수 반환
        return statistics.entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getAverage()
                ));
    }

    // 후보 영화에 개인화 점수 부여
    public double scoreGenrePreference(
            AiMovieCandidate candidate,
            Map<String, Double> genreWeights
    ) {
        if (candidate == null
                || candidate.genres() == null
                || genreWeights == null
                || genreWeights.isEmpty()) {
            return 0.0;
        }

        return candidate.genres()
                .stream()
                .filter(genre -> genre != null && !genre.isBlank())
                .map(this::normalize)
                .distinct()
                .filter(genreWeights::containsKey)
                .mapToDouble(genreWeights::get)
                .average()
                .orElse(0.0);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}