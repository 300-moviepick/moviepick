package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "RUN_OLLAMA_IT",
        matches = "true"
)
class SpringAiOllamaIntegrationTest {

    private SpringAiMovieReasonGenerator generator;

    @BeforeEach
    void setUp() {

        // ① 실제 로컬 Ollama 서버 연결
        OllamaApi ollamaApi = OllamaApi.builder()
                .build();

        // ② 실제 모델 생성
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(
                        OllamaChatOptions.builder()
                                .model("qwen3:4b")
                                .disableThinking()
                                .format("json")
                                .build()
                )
                .build();

        // ③ 기존 추천 이유 생성 서비스 연결
        ChatClient.Builder builder =
                ChatClient.builder(chatModel);

        generator = new SpringAiMovieReasonGenerator(builder);
    }

    @Test
    void 실제_Ollama가_영화_추천_이유를_생성한다() {

        AiMovieCandidate candidate = new AiMovieCandidate(
                1L,
                157336L,
                "인터스텔라",
                "인류의 생존을 위해 우주로 떠난 아버지의 이야기",
                LocalDate.of(2014, 11, 6),
                List.of("SF", "드라마", "모험"),
                8.5,
                900.0,
                null
        );

        // 실제 AI 호출
        List<AiMovieReason> result = generator.generate(
                "감동적인 우주 SF 영화를 추천해 줘",
                List.of(candidate)
        );

        // AI 응답 검증
        assertThat(result).hasSize(1);
        assertThat(result.get(0).movieId()).isEqualTo(1L);
        assertThat(result.get(0).reason()).isNotBlank();
        assertThat(result.get(0).reason().length())
                .isLessThanOrEqualTo(200);

        System.out.println("==================================");
        System.out.println("실제 Ollama 추천 결과");
        System.out.println("movieId: " + result.get(0).movieId());
        System.out.println("reason: " + result.get(0).reason());
        System.out.println("==================================");
    }
}