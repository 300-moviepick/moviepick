
package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReasonBatch;
import com.moviepick.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpringAiMovieReasonGeneratorTest {

    private ChatClient chatClient;
    private SpringAiMovieReasonGenerator generator;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder =
                mock(ChatClient.Builder.class);

        chatClient = mock(
                ChatClient.class,
                RETURNS_DEEP_STUBS
        );

        when(builder.build()).thenReturn(chatClient);

        generator = new SpringAiMovieReasonGenerator(builder);
    }

    @Test
    void 실제_후보_영화의_추천_이유를_반환한다() {

        mockAiResponse(new AiMovieReasonBatch(List.of(
                new AiMovieReason(2L, "인간관계의 감동을 그립니다."),
                new AiMovieReason(1L, "우주와 가족의 사랑을 다룹니다.")
        )));

        List<AiMovieReason> result = generator.generate(
                "감동적인 SF 영화를 추천해 줘",
                candidates()
        );

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(AiMovieReason::movieId)
                .containsExactly(1L, 2L);

        assertThat(result.get(0).reason())
                .contains("우주");
    }

    @Test
    void 후보에_없는_영화_ID는_거부한다() {

        mockAiResponse(new AiMovieReasonBatch(List.of(
                new AiMovieReason(999L, "알 수 없는 영화"),
                new AiMovieReason(2L, "감동적인 영화")
        )));

        assertThatThrownBy(() -> generator.generate(
                "감동적인 영화 추천",
                candidates()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 중복된_영화_ID는_거부한다() {

        mockAiResponse(new AiMovieReasonBatch(List.of(
                new AiMovieReason(1L, "첫 번째 이유"),
                new AiMovieReason(1L, "중복된 이유")
        )));

        assertThatThrownBy(() -> generator.generate(
                "SF 영화 추천",
                candidates()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 추천_이유가_누락되면_거부한다() {

        mockAiResponse(new AiMovieReasonBatch(List.of(
                new AiMovieReason(1L, "우주를 배경으로 합니다.")
        )));

        assertThatThrownBy(() -> generator.generate(
                "SF 영화 추천",
                candidates()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 빈_사용자_요청은_거부한다() {

        assertThatThrownBy(() -> generator.generate(
                " ",
                candidates()
        )).isInstanceOf(BusinessException.class);

        verifyNoInteractions(chatClient);
    }

    @Test
    void AI_호출_오류는_상위로_전달한다() {

        // [변경 ①] JSON Schema 옵션을 받는 entity() Mock
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .entity(eq(AiMovieReasonBatch.class), any()))
                .thenThrow(new IllegalStateException("AI 호출 실패"));

        assertThatThrownBy(() -> generator.generate(
                "SF 영화 추천",
                candidates()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 호출 실패");
    }

    // [변경 ②] 구조화 출력 옵션을 받는 Mock 응답
    private void mockAiResponse(AiMovieReasonBatch response) {
        when(chatClient.prompt()
                .system(anyString())
                .user(anyString())
                .call()
                .entity(eq(AiMovieReasonBatch.class), any()))
                .thenReturn(response);
    }

    private List<AiMovieCandidate> candidates() {
        return List.of(
                new AiMovieCandidate(
                        1L,
                        1001L,
                        "인터스텔라",
                        "우주를 탐험하며 가족을 그리워하는 이야기",
                        LocalDate.of(2014, 11, 6),
                        List.of("SF", "드라마"),
                        8.5,
                        900.0,
                        null
                ),
                new AiMovieCandidate(
                        2L,
                        1002L,
                        "감동 영화",
                        "인간관계와 희망을 다루는 이야기",
                        LocalDate.of(2024, 1, 1),
                        List.of("드라마"),
                        8.0,
                        500.0,
                        null
                )
        );
    }
}
