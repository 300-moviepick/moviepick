package com.moviepick.domain.ai.recommendation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private AiMovieEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        embeddingService =
                new AiMovieEmbeddingService(embeddingModel);
    }

    @Test
    void 사용자_요청을_그대로_임베딩한다() {

        String prompt = "감동적인 SF 영화를 추천해줘";

        float[] expected = new float[768];

        when(embeddingModel.embed(prompt))
                .thenReturn(expected);

        float[] actual =
                embeddingService.embedRequest("  " + prompt + "  ");

        assertThat(actual).isSameAs(expected);

        verify(embeddingModel).embed(prompt);
        verifyNoMoreInteractions(embeddingModel);
    }

    @Test
    void 빈_추천_요청은_거부한다() {

        assertThatThrownBy(() ->
                embeddingService.embedRequest("   ")
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("추천 요청 문장은 필수입니다.");

        verifyNoInteractions(embeddingModel);
    }

    @Test
    void null_추천_요청은_거부한다() {

        assertThatThrownBy(() ->
                embeddingService.embedRequest(null)
        ).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(embeddingModel);
    }
}