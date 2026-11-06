package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;
import com.moviepick.domain.ai.recommendation.entity.AiRecommendationFeedback;
import com.moviepick.domain.ai.recommendation.repository.AiRecommendationFeedbackRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.entity.MovieGenre;
import com.moviepick.domain.movie.repository.MovieGenreRepository;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiFeedbackRankingServiceTest {

    private static final Long MEMBER_ID = 2L;

    @Mock
    private AiRecommendationFeedbackRepository feedbackRepository;

    @Mock
    private MovieGenreRepository movieGenreRepository;

    @InjectMocks
    private AiFeedbackRankingService service;

    @Test
    void 좋아요_영화의_장르와_일치하면_가점을_준다() {

        AiRecommendationFeedback like = feedback(101L);
        MovieGenre sf = genre("SF");
        MovieGenre adventure = genre("모험");

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.LIKE
        )).willReturn(List.of(like));

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.DISLIKE
        )).willReturn(List.of());

        given(movieGenreRepository.findAllByMovie_IdIn(List.of(101L)))
                .willReturn(List.of(sf, adventure));

        var profile = service.loadProfile(MEMBER_ID);

        assertThat(profile.bonusFor(
                movie(201L, List.of("SF", "모험"))
        )).isEqualTo(4.0);

        assertThat(profile.bonusFor(
                movie(202L, List.of("로맨스"))
        )).isZero();
    }

    @Test
    void 싫어요_영화는_제외_대상으로_분류한다() {

        AiRecommendationFeedback dislike = feedback(101L);

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.LIKE
        )).willReturn(List.of());

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.DISLIKE
        )).willReturn(List.of(dislike));

        var profile = service.loadProfile(MEMBER_ID);

        assertThat(profile.isDisliked(
                movie(101L, List.of("SF"))
        )).isTrue();

        assertThat(profile.isDisliked(
                movie(102L, List.of("SF"))
        )).isFalse();

        verifyNoInteractions(movieGenreRepository);
    }

    @Test
    void 피드백이_없으면_가점과_제외_대상이_없다() {

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.LIKE
        )).willReturn(List.of());

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.DISLIKE
        )).willReturn(List.of());

        var profile = service.loadProfile(MEMBER_ID);

        assertThat(profile.dislikedMovieIds()).isEmpty();
        assertThat(profile.likedGenres()).isEmpty();

        assertThat(profile.bonusFor(
                movie(201L, List.of("SF"))
        )).isZero();

        verifyNoInteractions(movieGenreRepository);
    }

    @Test
    void 장르_가점은_최대_6점이다() {

        var profile = new AiFeedbackRankingService.FeedbackProfile(
                Set.of(),
                Set.of("sf", "모험", "드라마", "액션")
        );

        double bonus = profile.bonusFor(
                movie(
                        201L,
                        List.of("SF", "모험", "드라마", "액션")
                )
        );

        assertThat(bonus).isEqualTo(6.0);
    }

    @Test
    void 같은_영화_ID는_장르_조회에서_중복_제거한다() {

        AiRecommendationFeedback first = feedback(101L);
        AiRecommendationFeedback second = feedback(101L);

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.LIKE
        )).willReturn(List.of(first, second));

        given(feedbackRepository.findAllByMember_IdAndPreference(
                MEMBER_ID, AiFeedbackPreference.DISLIKE
        )).willReturn(List.of());

        given(movieGenreRepository.findAllByMovie_IdIn(List.of(101L)))
                .willReturn(List.of());

        service.loadProfile(MEMBER_ID);

        verify(movieGenreRepository)
                .findAllByMovie_IdIn(List.of(101L));
    }

    @Test
    void 잘못된_회원_ID는_거부한다() {

        assertThatThrownBy(() -> service.loadProfile(null))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> service.loadProfile(-1L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                feedbackRepository,
                movieGenreRepository
        );
    }

    private AiRecommendationFeedback feedback(Long movieId) {

        Movie movie = mock(Movie.class);
        when(movie.getId()).thenReturn(movieId);

        AiRecommendationFeedback feedback =
                mock(AiRecommendationFeedback.class);

        when(feedback.getMovie()).thenReturn(movie);

        return feedback;
    }

    private MovieGenre genre(String name) {

        MovieGenre genre = mock(MovieGenre.class);
        when(genre.getGenreName()).thenReturn(name);

        return genre;
    }

    private AiMovieCandidate movie(
            Long id,
            List<String> genres
    ) {
        return new AiMovieCandidate(
                id,
                id + 1000,
                "테스트 영화",
                "테스트 줄거리",
                LocalDate.of(2025, 1, 1),
                genres,
                8.0,
                100.0,
                null
        );
    }
}