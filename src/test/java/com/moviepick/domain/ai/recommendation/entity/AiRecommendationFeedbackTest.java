package com.moviepick.domain.ai.recommendation.entity;

import com.moviepick.domain.member.entity.Member;
import com.moviepick.domain.movie.entity.Movie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AiRecommendationFeedbackTest {

    private final Member member = Member.createLocal(
            "feedback-test@example.com",
            "encoded-password",
            "feedbackTester"
    );

    private final Movie movie = new Movie(
            157336L,
            "인터스텔라"
    );

    @Test
    void 좋아요_피드백을_생성한다() {

        AiRecommendationFeedback feedback =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        "우주 탐험 이야기가 좋아요."
                );

        assertThat(feedback.getMember()).isSameAs(member);
        assertThat(feedback.getMovie()).isSameAs(movie);
        assertThat(feedback.getPreference())
                .isEqualTo(AiFeedbackPreference.LIKE);
        assertThat(feedback.getReason())
                .isEqualTo("우주 탐험 이야기가 좋아요.");
    }

    @Test
    void 기존_좋아요를_싫어요로_변경한다() {

        AiRecommendationFeedback feedback =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        null
                );

        feedback.change(
                AiFeedbackPreference.DISLIKE,
                "취향에 맞지 않아요."
        );

        assertThat(feedback.getPreference())
                .isEqualTo(AiFeedbackPreference.DISLIKE);

        assertThat(feedback.getReason())
                .isEqualTo("취향에 맞지 않아요.");
    }

    @Test
    void 사유는_작성하지_않아도_된다() {

        AiRecommendationFeedback feedback =
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        " "
                );

        assertThat(feedback.getReason()).isNull();
    }

    @Test
    void 사유가_500자를_초과하면_거부한다() {

        String longReason = "가".repeat(501);

        assertThatThrownBy(() ->
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        AiFeedbackPreference.LIKE,
                        longReason
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 피드백_종류가_없으면_거부한다() {

        assertThatThrownBy(() ->
                AiRecommendationFeedback.create(
                        member,
                        movie,
                        null,
                        "추천 이유"
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 회원이나_영화가_없으면_거부한다() {

        assertThatThrownBy(() ->
                AiRecommendationFeedback.create(
                        null,
                        movie,
                        AiFeedbackPreference.LIKE,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                AiRecommendationFeedback.create(
                        member,
                        null,
                        AiFeedbackPreference.LIKE,
                        null
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}