package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiFeedbackRequest;
import com.moviepick.domain.ai.recommendation.entity.AiFeedbackPreference;
import com.moviepick.domain.ai.recommendation.entity.AiRecommendationFeedback;
import com.moviepick.domain.ai.recommendation.repository.AiRecommendationFeedbackRepository;
import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.member.repository.MemberRepository;
import com.moviepick.domain.movie.entity.Movie;
import com.moviepick.domain.movie.repository.MovieRepository;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRecommendationFeedbackServiceTest {

    private static final Long MEMBER_ID = 2L;
    private static final Long MOVIE_ID = 60L;

    @Mock
    private AiRecommendationFeedbackRepository feedbackRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private AiRecommendationFeedbackService feedbackService;

    @Test
    void 피드백이_없으면_새로_등록한다() {
        Member member = member();
        Movie movie = movie();

        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));

        given(movieRepository.findByIdAndActiveTrue(MOVIE_ID))
                .willReturn(Optional.of(movie));

        var result = feedbackService.upsert(
                MEMBER_ID,
                MOVIE_ID,
                new AiFeedbackRequest(
                        AiFeedbackPreference.LIKE,
                        "마음에 드는 영화"
                )
        );

        ArgumentCaptor<AiRecommendationFeedback> captor =
                ArgumentCaptor.forClass(
                        AiRecommendationFeedback.class
                );

        verify(feedbackRepository).save(captor.capture());

        AiRecommendationFeedback saved = captor.getValue();

        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getMovie()).isSameAs(movie);
        assertThat(saved.getPreference())
                .isEqualTo(AiFeedbackPreference.LIKE);

        assertThat(result.movieId()).isEqualTo(MOVIE_ID);
        assertThat(result.reason()).isEqualTo("마음에 드는 영화");
    }

    @Test
    void 기존_좋아요를_싫어요로_수정한다() {
        Member member = member();
        Movie movie = movie();

        AiRecommendationFeedback existing =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        null
                );

        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));

        given(movieRepository.findByIdAndActiveTrue(MOVIE_ID))
                .willReturn(Optional.of(movie));

        given(feedbackRepository.findByMember_IdAndMovie_Id(
                MEMBER_ID, MOVIE_ID
        )).willReturn(Optional.of(existing));

        var result = feedbackService.upsert(
                MEMBER_ID,
                MOVIE_ID,
                new AiFeedbackRequest(
                        AiFeedbackPreference.DISLIKE,
                        "취향에 맞지 않아요"
                )
        );

        assertThat(result.preference())
                .isEqualTo(AiFeedbackPreference.DISLIKE);

        assertThat(existing.getPreference())
                .isEqualTo(AiFeedbackPreference.DISLIKE);

        verify(feedbackRepository, never())
                .save(any(AiRecommendationFeedback.class));
    }

    @Test
    void 같은_피드백을_다시_등록해도_추가_INSERT하지_않는다() {
        Member member = member();
        Movie movie = movie();

        AiRecommendationFeedback existing =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        null
                );

        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));

        given(movieRepository.findByIdAndActiveTrue(MOVIE_ID))
                .willReturn(Optional.of(movie));

        given(feedbackRepository.findByMember_IdAndMovie_Id(
                MEMBER_ID, MOVIE_ID
        )).willReturn(Optional.of(existing));

        feedbackService.upsert(
                MEMBER_ID,
                MOVIE_ID,
                new AiFeedbackRequest(
                        AiFeedbackPreference.LIKE,
                        null
                )
        );

        verify(feedbackRepository, never())
                .save(any(AiRecommendationFeedback.class));
    }

    @Test
    void 자신의_피드백을_삭제한다() {
        Member member = member();
        Movie movie = movie();

        AiRecommendationFeedback existing =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        null
                );

        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member));

        given(feedbackRepository.findByMember_IdAndMovie_Id(
                MEMBER_ID, MOVIE_ID
        )).willReturn(Optional.of(existing));

        feedbackService.delete(MEMBER_ID, MOVIE_ID);

        verify(feedbackRepository).delete(existing);
        verifyNoInteractions(movieRepository);
    }

    @Test
    void 피드백이_없으면_삭제하지_않는다() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member()));

        assertThatThrownBy(() ->
                feedbackService.delete(MEMBER_ID, MOVIE_ID)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.NOT_FOUND)
        );

        verify(feedbackRepository, never())
                .delete(any(AiRecommendationFeedback.class));
    }

    @Test
    void 다른_회원의_피드백은_삭제할_수_없다() {
        // 로그인 회원 ID로 조회했을 때 자신의 피드백이 없는 상황
        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member()));

        assertThatThrownBy(() ->
                feedbackService.delete(MEMBER_ID, MOVIE_ID)
        ).isInstanceOf(BusinessException.class);

        verify(feedbackRepository)
                .findByMember_IdAndMovie_Id(MEMBER_ID, MOVIE_ID);

        verify(feedbackRepository, never())
                .delete(any(AiRecommendationFeedback.class));
    }

    @Test
    void 존재하지_않는_영화는_피드백을_등록하지_않는다() {
        given(memberRepository.findByIdForUpdate(MEMBER_ID))
                .willReturn(Optional.of(member()));

        assertThatThrownBy(() ->
                feedbackService.upsert(
                        MEMBER_ID,
                        MOVIE_ID,
                        new AiFeedbackRequest(
                                AiFeedbackPreference.LIKE,
                                null
                        )
                )
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(feedbackRepository);
    }

    @Test
    void 존재하지_않는_회원은_등록할_수_없다() {
        assertThatThrownBy(() ->
                feedbackService.upsert(
                        MEMBER_ID,
                        MOVIE_ID,
                        new AiFeedbackRequest(
                                AiFeedbackPreference.LIKE,
                                null
                        )
                )
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                movieRepository,
                feedbackRepository
        );
    }

    @Test
    void 잘못된_입력은_거부한다() {
        assertThatThrownBy(() ->
                feedbackService.upsert(
                        MEMBER_ID,
                        MOVIE_ID,
                        new AiFeedbackRequest(null, null)
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_INPUT)
        );

        assertThatThrownBy(() ->
                feedbackService.upsert(
                        MEMBER_ID,
                        MOVIE_ID,
                        new AiFeedbackRequest(
                                AiFeedbackPreference.LIKE,
                                "가".repeat(501)
                        )
                )
        ).isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                memberRepository,
                movieRepository,
                feedbackRepository
        );
    }

    private Member member() {
        return Member.createLocal(
                "feedback@example.com",
                "encoded-password",
                "feedbackTester"
        );
    }

    private Movie movie() {
        return new Movie(157336L, "인터스텔라");
    }
}