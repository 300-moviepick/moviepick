package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @InjectMocks
    private AiMovieEmbeddingService embeddingService;

    @Test
    void 사용자_요청을_벡터로_변환한다() {

        String text =
                "task: search result | query: 우주 탐험 영화 추천";

        float[] expected = {0.1f, 0.2f, 0.3f};

        given(embeddingModel.embed(text))
                .willReturn(expected);

        float[] result = embeddingService.embedRequest(
                "우주 탐험 영화 추천"
        );

        assertThat(result).containsExactly(expected);

        verify(embeddingModel).embed(text);
    }

    @Test
    void 영화_정보를_벡터로_변환한다() {

        AiMovieCandidate movie = new AiMovieCandidate(
                60L,
                157336L,
                "인터스텔라",
                "우주 여행과 가족 이야기",
                LocalDate.of(2014, 11, 6),
                List.of("SF", "드라마"),
                8.6,
                120.0,
                null
        );

        String expectedText =
                "title: 인터스텔라"
                        + " | text: 줄거리: 우주 여행과 가족 이야기"
                        + "\n장르: SF, 드라마";

        float[] expected = {0.4f, 0.5f, 0.6f};

        given(embeddingModel.embed(expectedText))
                .willReturn(expected);

        float[] result = embeddingService.embedMovie(movie);

        assertThat(result).containsExactly(expected);

        verify(embeddingModel).embed(expectedText);
    }

    @Test
    void 빈_요청은_거부한다() {

        assertThatThrownBy(() ->
                embeddingService.embedRequest(" ")
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingModel);
    }

    @Test
    void 영화_정보가_없으면_거부한다() {

        assertThatThrownBy(() ->
                embeddingService.embedMovie(null)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingModel);
    }
}