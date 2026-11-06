package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReasonBatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SpringAiMovieReasonValidationTest {

    @Test
    void 모든_후보의_추천_이유가_있으면_후보_순서를_유지한다() {

        var response = batch(
                reason(2L, "우주 탐험과 모험을 다룹니다."),
                reason(1L, "가족의 사랑을 그립니다.")
        );

        var result = SpringAiMovieReasonGenerator.validateResponse(
                response,
                List.of(movie(1L), movie(2L))
        );

        assertThat(result)
                .extracting(AiMovieReason::movieId)
                .containsExactly(1L, 2L);

        assertThat(result.get(0).reason())
                .isEqualTo("가족의 사랑을 그립니다.");
    }

    @Test
    void 영화_이유가_누락되면_누락_ID를_알린다() {

        var response = batch(
                reason(1L, "추천 이유")
        );

        assertThatThrownBy(() ->
                SpringAiMovieReasonGenerator.validateResponse(
                        response,
                        List.of(movie(1L), movie(2L))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("누락")
                .hasMessageContaining("2");
    }

    @Test
    void 같은_영화_ID가_두_번_나오면_거부한다() {

        var response = batch(
                reason(1L, "첫 번째 이유"),
                reason(1L, "두 번째 이유")
        );

        assertThatThrownBy(() ->
                SpringAiMovieReasonGenerator.validateResponse(
                        response,
                        List.of(movie(1L), movie(2L))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void 존재하지_않는_영화_ID는_거부한다() {

        var response = batch(
                reason(999L, "이유")
        );

        assertThatThrownBy(() ->
                SpringAiMovieReasonGenerator.validateResponse(
                        response,
                        List.of(movie(1L))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잘못된 추천 이유");
    }

    @Test
    void 빈_응답을_거부한다() {

        assertThatThrownBy(() ->
                SpringAiMovieReasonGenerator.validateResponse(
                        null,
                        List.of(movie(1L))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("비어");
    }

    @Test
    void 추천_이유는_200자를_넘을_수_없다() {

        var response = batch(
                reason(1L, "가".repeat(201))
        );

        assertThatThrownBy(() ->
                SpringAiMovieReasonGenerator.validateResponse(
                        response,
                        List.of(movie(1L))
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잘못된 추천 이유");
    }

    private AiMovieReasonBatch batch(AiMovieReason... reasons) {
        return new AiMovieReasonBatch(List.of(reasons));
    }

    private AiMovieReason reason(Long movieId, String content) {
        return new AiMovieReason(movieId, content);
    }

    private AiMovieCandidate movie(Long id) {
        return new AiMovieCandidate(
                id,
                id + 1000,
                "테스트 영화 " + id,
                "우주를 배경으로 한 모험",
                LocalDate.of(2025, 1, 1),
                List.of("SF"),
                8.0,
                100.0,
                null
        );
    }
}