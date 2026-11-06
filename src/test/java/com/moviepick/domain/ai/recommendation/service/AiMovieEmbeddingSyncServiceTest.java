package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingSyncServiceTest {

    private static final String MODEL = "embeddinggemma:300m";
    private static final String CONTENT =
            "title: 인터스텔라 | text: 줄거리: 우주 여행\n장르: SF, 드라마";

    @Mock
    private AiMovieCandidateService candidateService;

    @Mock
    private AiMovieEmbeddingService embeddingService;

    @Mock
    private AiMovieEmbeddingRepository embeddingRepository;

    private AiMovieEmbeddingSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new AiMovieEmbeddingSyncService(
                candidateService,
                embeddingService,
                embeddingRepository,
                MODEL
        );
    }

    @Test
    void 처음_수집하는_영화는_임베딩을_생성하여_저장한다() {

        AiMovieCandidate movie = movie();

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of(movie));

        given(embeddingService.buildMovieText(movie))
                .willReturn(CONTENT);

        given(embeddingRepository.findByMovieId(60L))
                .willReturn(Optional.empty());

        given(embeddingService.embedMovie(movie))
                .willReturn(new float[]{0.1f, 0.2f, 0.3f});

        var result = syncService.sync(10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();

        ArgumentCaptor<String> hashCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(embeddingRepository).upsert(
                eq(60L),
                eq(MODEL),
                hashCaptor.capture(),
                any(float[].class)
        );

        assertThat(hashCaptor.getValue())
                .matches("[0-9a-f]{64}");
    }

    @Test
    void 영화_내용과_모델이_같으면_재임베딩을_생략한다()
            throws Exception {

        AiMovieCandidate movie = movie();

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of(movie));

        given(embeddingService.buildMovieText(movie))
                .willReturn(CONTENT);

        var stored = new AiMovieEmbeddingRepository.StoredEmbedding(
                60L,
                MODEL,
                sha256(CONTENT),
                new float[]{0.1f, 0.2f, 0.3f}
        );

        given(embeddingRepository.findByMovieId(60L))
                .willReturn(Optional.of(stored));

        var result = syncService.sync(10);

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();

        verify(embeddingService, never())
                .embedMovie(any());

        verify(embeddingRepository, never())
                .upsert(any(), any(), any(), any());
    }

    @Test
    void 영화_내용이_변경되면_임베딩을_갱신한다() {

        AiMovieCandidate movie = movie();

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of(movie));

        given(embeddingService.buildMovieText(movie))
                .willReturn(CONTENT);

        var stored = new AiMovieEmbeddingRepository.StoredEmbedding(
                60L,
                MODEL,
                "a".repeat(64),
                new float[]{0.1f, 0.2f, 0.3f}
        );

        given(embeddingRepository.findByMovieId(60L))
                .willReturn(Optional.of(stored));

        given(embeddingService.embedMovie(movie))
                .willReturn(new float[]{0.4f, 0.5f, 0.6f});

        var result = syncService.sync(10);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();

        verify(embeddingRepository).upsert(
                eq(60L),
                eq(MODEL),
                anyString(),
                any(float[].class)
        );
    }

    @Test
    void 임베딩_생성에_실패하면_실패_건수를_기록한다() {

        AiMovieCandidate movie = movie();

        given(candidateService.findCandidatesForRanking())
                .willReturn(List.of(movie));

        given(embeddingService.buildMovieText(movie))
                .willReturn(CONTENT);

        given(embeddingRepository.findByMovieId(60L))
                .willReturn(Optional.empty());

        given(embeddingService.embedMovie(movie))
                .willThrow(
                        new IllegalStateException("Ollama 호출 실패")
                );

        var result = syncService.sync(10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.created()).isZero();

        verify(embeddingRepository, never())
                .upsert(any(), any(), any(), any());
    }

    @Test
    void 잘못된_수집_개수는_거부한다() {

        assertThatThrownBy(() -> syncService.sync(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> syncService.sync(101))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(
                candidateService,
                embeddingService,
                embeddingRepository
        );
    }

    private AiMovieCandidate movie() {
        return new AiMovieCandidate(
                60L,
                157336L,
                "인터스텔라",
                "우주 여행",
                LocalDate.of(2014, 11, 6),
                List.of("SF", "드라마"),
                8.6,
                100.0,
                null
        );
    }

    private String sha256(String text) throws Exception {
        byte[] hash = MessageDigest
                .getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hash);
    }
}