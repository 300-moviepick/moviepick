package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;
import com.moviepick.domain.ai.recommendation.entity.AiRecommendationFeedback;
import com.moviepick.domain.ai.recommendation.repository.AiRecommendationFeedbackRepository;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiFeedbackRankingService {

    private final AiRecommendationFeedbackRepository feedbackRepository;
    private final MovieGenreRepository movieGenreRepository;

    // 회원의 피드백을 추천 점수 계산용 데이터로 변환
    @Transactional(readOnly = true)
    public FeedbackProfile loadProfile(Long memberId) {

        if (memberId == null || memberId <= 0) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        List<AiRecommendationFeedback> likes =
                feedbackRepository.findAllByMember_IdAndPreference(
                        memberId,
                        AiFeedbackPreference.LIKE
                );

        List<AiRecommendationFeedback> dislikes =
                feedbackRepository.findAllByMember_IdAndPreference(
                        memberId,
                        AiFeedbackPreference.DISLIKE
                );

        // ① DISLIKE 영화 ID 수집
        Set<Long> dislikedMovieIds = dislikes.stream()
                .map(feedback -> feedback.getMovie().getId())
                .collect(Collectors.toUnmodifiableSet());

        // ② LIKE 영화 ID 수집
        List<Long> likedMovieIds = likes.stream()
                .map(feedback -> feedback.getMovie().getId())
                .distinct()
                .toList();

        if (likedMovieIds.isEmpty()) {
            return new FeedbackProfile(
                    dislikedMovieIds,
                    Set.of()
            );
        }

        // ③ LIKE 영화들의 장르를 한 번에 조회
        Set<String> likedGenres = movieGenreRepository
                .findAllByMovie_IdIn(likedMovieIds)
                .stream()
                .map(MovieGenre::getGenreName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(genre -> !genre.isBlank())
                .map(genre -> genre.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return new FeedbackProfile(
                dislikedMovieIds,
                likedGenres
        );
    }

    // 추천 요청 한 번에 사용할 불변 프로필
    public record FeedbackProfile(
            Set<Long> dislikedMovieIds,
            Set<String> likedGenres
    ) {

        public FeedbackProfile {
            dislikedMovieIds = Set.copyOf(dislikedMovieIds);
            likedGenres = Set.copyOf(likedGenres);
        }

        // DISLIKE 영화인지 확인
        public boolean isDisliked(AiMovieCandidate movie) {
            return dislikedMovieIds.contains(movie.movieId());
        }

        // LIKE 영화와 장르가 일치하면 가점
        public double bonusFor(AiMovieCandidate movie) {

            if (movie.genres() == null || likedGenres.isEmpty()) {
                return 0.0;
            }

            long matchingGenres = movie.genres().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(genre -> !genre.isBlank())
                    .map(genre -> genre.toLowerCase(Locale.ROOT))
                    .distinct()
                    .filter(likedGenres::contains)
                    .count();

            // 일치 장르당 +2점, 최대 +6점
            return Math.min(6.0, matchingGenres * 2.0);
        }
    }
}