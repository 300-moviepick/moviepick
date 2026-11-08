package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiMovieRankingService {

    private static final int MAX_RESULTS = 20;

    private static final int GENRE_SCORE = 10;
    private static final int TITLE_SCORE = 5;
    private static final int OVERVIEW_SCORE = 2;

    private static final Pattern WORD_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "영화", "추천", "해줘", "해주세요",
            "찾아줘", "보고", "싶어", "작품",
            "장르", "관련", "같은", "느낌",
            "이야기", "그런", "좋아하는"
    );

    private static final List<String> SUFFIXES = List.of(
            "적인", "에서", "으로", "에게",
            "부터", "까지", "처럼",
            "를", "을", "은", "는",
            "이", "가", "의", "에", "도"
    );

    private static final List<GenreRule> GENRE_RULES = List.of(
            new GenreRule(
                    List.of("sf", "공상과학", "사이파이", "science fiction"),
                    List.of("sf", "공상과학", "science fiction")
            ),
            new GenreRule(
                    List.of("드라마", "drama"),
                    List.of("드라마", "drama")
            ),
            new GenreRule(
                    List.of("로맨스", "멜로", "연애", "romance"),
                    List.of("로맨스", "romance")
            ),
            new GenreRule(
                    List.of("액션", "action"),
                    List.of("액션", "action")
            ),
            new GenreRule(
                    List.of("공포", "호러", "horror"),
                    List.of("공포", "horror")
            ),
            new GenreRule(
                    List.of("코미디", "comedy"),
                    List.of("코미디", "comedy")
            ),
            new GenreRule(
                    List.of("판타지", "fantasy"),
                    List.of("판타지", "fantasy")
            ),
            new GenreRule(
                    List.of("모험", "adventure"),
                    List.of("모험", "adventure")
            ),
            new GenreRule(
                    List.of("스릴러", "thriller"),
                    List.of("스릴러", "thriller")
            ),
            new GenreRule(
                    List.of("애니메이션", "애니", "animation"),
                    List.of("애니메이션", "animation")
            ),
            new GenreRule(
                    List.of("범죄", "crime"),
                    List.of("범죄", "crime")
            ),
            new GenreRule(
                    List.of("미스터리", "추리", "mystery"),
                    List.of("미스터리", "mystery")
            )
    );

    // 기존 호출 방식 유지
    public List<AiMovieCandidate> rank(
            String userRequest,
            List<AiMovieCandidate> candidates,
            int limit
    ) {
        return rank(
                userRequest,
                candidates,
                limit,
                movie -> 0.0
        );
    }

    // 요청 관련성과 회원 개인화 점수를 합산
    public List<AiMovieCandidate> rank(
            String userRequest,
            List<AiMovieCandidate> candidates,
            int limit,
            ToDoubleFunction<AiMovieCandidate> personalizationScore
    ) {
        validate(userRequest, candidates, limit);

        if (personalizationScore == null) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        String request = normalize(userRequest);
        List<String> keywords = extractKeywords(request);

        LinkedHashMap<Long, AiMovieCandidate> unique =
                new LinkedHashMap<>();

        for (AiMovieCandidate movie : candidates) {
            if (movie == null || movie.movieId() == null) {
                throw new BusinessException(
                        CommonErrorCode.INVALID_INPUT
                );
            }

            unique.putIfAbsent(movie.movieId(), movie);
        }

        // 영화마다 최종 점수를 한 번씩 계산
        List<ScoredMovie> scoredMovies = new ArrayList<>();

        for (AiMovieCandidate movie : unique.values()) {
            double relevance = calculateScore(
                    request, keywords, movie
            );

            double preference =
                    personalizationScore.applyAsDouble(movie);

            if (!Double.isFinite(preference)) {
                throw new BusinessException(
                        CommonErrorCode.INVALID_INPUT
                );
            }

            scoredMovies.add(new ScoredMovie(
                    movie,
                    relevance + preference
            ));
        }

        scoredMovies.sort(
                Comparator
                        .comparingDouble(ScoredMovie::score)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        (ScoredMovie item) ->
                                                safePopularity(
                                                        item.movie().popularity()
                                                )
                                ).reversed()
                        )
                        .thenComparing(
                                item -> item.movie().movieId()
                        )
        );

        return scoredMovies.stream()
                .limit(limit)
                .map(ScoredMovie::movie)
                .toList();
    }

    private int calculateScore(
            String request,
            List<String> keywords,
            AiMovieCandidate movie
    ) {
        int score = 0;

        String title = normalize(movie.title());
        String overview = normalize(movie.overview());

        for (GenreRule rule : GENRE_RULES) {
            boolean requested = rule.requestWords()
                    .stream()
                    .anyMatch(request::contains);

            boolean genreMatched = movie.genres() != null
                    && movie.genres().stream()
                    .filter(genre -> genre != null)
                    .map(this::normalize)
                    .anyMatch(genre ->
                            rule.genreNames()
                                    .stream()
                                    .anyMatch(genre::contains)
                    );

            if (requested && genreMatched) {
                score += GENRE_SCORE;
            }
        }

        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                score += TITLE_SCORE;
            }

            if (overview.contains(keyword)) {
                score += OVERVIEW_SCORE;
            }
        }

        return score;
    }

    private List<String> extractKeywords(String request) {
        Set<String> keywords = new LinkedHashSet<>();
        Matcher matcher = WORD_PATTERN.matcher(request);

        while (matcher.find()) {
            String keyword = removeSuffix(matcher.group());

            if (keyword.length() >= 2
                    && !STOP_WORDS.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return List.copyOf(keywords);
    }

    private String removeSuffix(String word) {
        for (String suffix : SUFFIXES) {
            if (word.endsWith(suffix)
                    && word.length() - suffix.length() >= 2) {
                return word.substring(
                        0,
                        word.length() - suffix.length()
                );
            }
        }

        return word;
    }

    private double safePopularity(Double popularity) {
        if (popularity == null || !Double.isFinite(popularity)) {
            return 0.0;
        }

        return Math.max(popularity, 0.0);
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).trim();
    }

    private void validate(
            String userRequest,
            List<AiMovieCandidate> candidates,
            int limit
    ) {
        if (userRequest == null
                || userRequest.isBlank()
                || candidates == null
                || limit < 1
                || limit > MAX_RESULTS) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }

    private record GenreRule(
            List<String> requestWords,
            List<String> genreNames
    ) {
    }

    private record ScoredMovie(
            AiMovieCandidate movie,
            double score
    ) {
    }
}