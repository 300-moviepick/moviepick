package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReason;
import com.moviepick.domain.ai.recommendation.dto.AiMovieReasonBatch;
import com.moviepick.global.exception.BusinessException;
import com.moviepick.global.exception.CommonErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(
        name = "spring.ai.model.chat",
        havingValue = "ollama"
)
public class SpringAiMovieReasonGenerator
        implements AiMovieReasonGenerator {

    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_REQUEST_LENGTH = 500;

    private static final String SYSTEM_PROMPT = """
        당신은 영화 추천 서비스 MoviePick의 AI입니다.

        규칙:
        1. 제공된 영화 후보만 사용하세요.
        2. 모든 후보 영화에 추천 이유를 정확히 하나씩 작성하세요.
        3. movieId를 변경하거나 새로 만들지 마세요.
        4. 사용자 요청과 영화 줄거리, 장르를 참고하세요.
        5. 추천 이유는 한국어 200자 이내로 작성하세요.
        6. 확인되지 않은 영화 내용은 지어내지 마세요.
        7. 스포일러를 피하세요.

        응답 구조:
        - 최상위 응답은 반드시 JSON 객체여야 합니다.
        - 최상위 배열을 반환하지 마세요.
        - 최상위 객체에는 reasons 필드가 있어야 합니다.
        - reasons는 영화별 movieId와 reason을 포함하는 배열입니다.
        - JSON 이외의 문장이나 마크다운은 반환하지 마세요.
        """;

    private final ChatClient chatClient;

    public SpringAiMovieReasonGenerator(
            ChatClient.Builder builder
    ) {
        this.chatClient = builder.build();
    }

    @Override
    public List<AiMovieReason> generate(
            String userRequest,
            List<AiMovieCandidate> candidates
    ) {
        validateInput(userRequest, candidates);

        // ① 실제 AI 호출
        AiMovieReasonBatch response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildPrompt(userRequest.trim(), candidates))
                .call()
                .entity(
                        AiMovieReasonBatch.class,
                        spec -> spec
                                .useProviderStructuredOutput()
                                .validateSchema()
                );

        // ② AI 응답 검증
        return validateResponse(response, candidates);
    }

    private void validateInput(
            String userRequest,
            List<AiMovieCandidate> candidates
    ) {
        if (userRequest == null
                || userRequest.isBlank()
                || userRequest.length() > MAX_REQUEST_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (candidates == null
                || candidates.isEmpty()
                || candidates.size() > MAX_CANDIDATES) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Set<Long> ids = new HashSet<>();

        for (AiMovieCandidate candidate : candidates) {
            if (candidate == null
                    || candidate.movieId() == null
                    || !ids.add(candidate.movieId())) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
        }
    }

    private String buildPrompt(
            String userRequest,
            List<AiMovieCandidate> candidates
    ) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("사용자 요청:\n")
                .append(userRequest)
                .append("\n\n영화 후보 목록:\n");

        for (AiMovieCandidate movie : candidates) {
            String genres = movie.genres() == null
                    ? ""
                    : String.join(", ", movie.genres());

            prompt.append("\nmovieId: ")
                    .append(movie.movieId())
                    .append("\n제목: ")
                    .append(clean(movie.title(), 120))
                    .append("\n장르: ")
                    .append(clean(genres, 150))
                    .append("\n줄거리: ")
                    .append(clean(movie.overview(), 400))
                    .append("\n");
        }

        return prompt.toString();
    }

    private String clean(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "정보 없음";
        }

        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        return normalized.substring(
                0,
                Math.min(normalized.length(), maxLength)
        );
    }

    private List<AiMovieReason> validateResponse(
            AiMovieReasonBatch response,
            List<AiMovieCandidate> candidates
    ) {
        if (response == null || response.reasons() == null) {
            throw new IllegalStateException(
                    "AI 추천 이유 응답이 비어 있습니다."
            );
        }

        Set<Long> expectedIds = new HashSet<>();

        for (AiMovieCandidate candidate : candidates) {
            expectedIds.add(candidate.movieId());
        }

        Map<Long, String> reasonMap = new HashMap<>();

        for (AiMovieReason item : response.reasons()) {
            if (item == null
                    || item.movieId() == null
                    || !expectedIds.contains(item.movieId())
                    || item.reason() == null
                    || item.reason().isBlank()
                    || item.reason().length() > 200) {
                throw new IllegalStateException(
                        "AI가 잘못된 추천 이유를 반환했습니다."
                );
            }

            String previous = reasonMap.putIfAbsent(
                    item.movieId(),
                    item.reason().trim()
            );

            if (previous != null) {
                throw new IllegalStateException(
                        "AI 응답에 중복된 영화 ID가 있습니다."
                );
            }
        }

        if (reasonMap.size() != expectedIds.size()) {
            throw new IllegalStateException(
                    "일부 영화의 추천 이유가 누락되었습니다."
            );
        }

        // 기존 추천 후보 순서 유지
        return candidates.stream()
                .map(candidate -> new AiMovieReason(
                        candidate.movieId(),
                        reasonMap.get(candidate.movieId())
                ))
                .toList();
    }
}