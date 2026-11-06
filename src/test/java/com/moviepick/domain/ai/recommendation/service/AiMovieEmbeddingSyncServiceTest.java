package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingSyncServiceTest {

    private static final Long MOVIE_ID = 1L;
    private static final String MODEL_NAME = "text-embedding-3-small";
    private static final int DIMENSIONS = 768;

    private static final String MOVIE_TEXT =
            "title: 테스트 영화 | text: 줄거리: 우주를 탐험하는 이야기\n장르: SF";

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
                MODEL_NAME,
                DIMENSIONS
        );
    }

    @Test
    void 처음_수집하는_영화는_임베딩을_생성하여_저장한다()
            throws Exception {

        AiMovieCandidate movie = createMovie();
        float[] vector = createVector();

        when(movie.title()).thenReturn("테스트 영화");

        when(embeddingService.buildMovieText(movie))
                .thenReturn(MOVIE_TEXT);

        when(embeddingRepository.findByMovieId(MOVIE_ID))
                .thenReturn(Optional.empty());

        when(embeddingService.embedMovie(movie))
                .thenReturn(vector);

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.syncMovies(List.of(movie));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();

        verify(embeddingService).embedMovie(movie);

        verify(embeddingRepository).upsert(
                eq(MOVIE_ID),
                eq(MODEL_NAME),
                eq(sha256(MOVIE_TEXT)),
                same(vector)
        );
    }

    @Test
    void 영화_내용과_모델이_같으면_재임베딩을_생략한다()
            throws Exception {

        AiMovieCandidate movie = createMovie();

        AiMovieEmbeddingRepository.StoredEmbedding existing =
                mock(AiMovieEmbeddingRepository.StoredEmbedding.class);

        when(movie.title()).thenReturn("테스트 영화");

        when(embeddingService.buildMovieText(movie))
                .thenReturn(MOVIE_TEXT);

        when(embeddingRepository.findByMovieId(MOVIE_ID))
                .thenReturn(Optional.of(existing));

        when(existing.modelName())
                .thenReturn(MODEL_NAME);

        when(existing.contentHash())
                .thenReturn(sha256(MOVIE_TEXT));

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.syncMovies(List.of(movie));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();

        verify(embeddingService, never())
                .embedMovie(any());

        verify(embeddingRepository, never())
                .upsert(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(float[].class)
                );
    }

    @Test
    void 영화_내용이_변경되면_임베딩을_갱신한다()
            throws Exception {

        AiMovieCandidate movie = createMovie();
        float[] vector = createVector();

        AiMovieEmbeddingRepository.StoredEmbedding existing =
                mock(AiMovieEmbeddingRepository.StoredEmbedding.class);

        when(movie.title()).thenReturn("테스트 영화");

        when(embeddingService.buildMovieText(movie))
                .thenReturn(MOVIE_TEXT);

        when(embeddingRepository.findByMovieId(MOVIE_ID))
                .thenReturn(Optional.of(existing));

        when(existing.modelName())
                .thenReturn(MODEL_NAME);

        when(existing.contentHash())
                .thenReturn(sha256("변경 전 영화 줄거리"));

        when(embeddingService.embedMovie(movie))
                .thenReturn(vector);

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.syncMovies(List.of(movie));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();

        verify(embeddingService).embedMovie(movie);

        verify(embeddingRepository).upsert(
                eq(MOVIE_ID),
                eq(MODEL_NAME),
                eq(sha256(MOVIE_TEXT)),
                same(vector)
        );
    }

    @Test
    void 임베딩_모델이_변경되면_영화를_재임베딩한다()
            throws Exception {

        AiMovieCandidate movie = createMovie();
        float[] vector = createVector();

        AiMovieEmbeddingRepository.StoredEmbedding existing =
                mock(AiMovieEmbeddingRepository.StoredEmbedding.class);

        when(movie.title()).thenReturn("테스트 영화");

        when(embeddingService.buildMovieText(movie))
                .thenReturn(MOVIE_TEXT);

        when(embeddingRepository.findByMovieId(MOVIE_ID))
                .thenReturn(Optional.of(existing));

        when(existing.modelName())
                .thenReturn("embeddinggemma:300m");

        when(embeddingService.embedMovie(movie))
                .thenReturn(vector);

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.syncMovies(List.of(movie));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();

        verify(embeddingService).embedMovie(movie);

        verify(embeddingRepository).upsert(
                eq(MOVIE_ID),
                eq(MODEL_NAME),
                eq(sha256(MOVIE_TEXT)),
                same(vector)
        );
    }

    @Test
    void 임베딩_차원이_다르면_저장하지_않는다() {

        AiMovieCandidate movie = createMovie();

        when(embeddingService.buildMovieText(movie))
                .thenReturn(MOVIE_TEXT);

        when(embeddingRepository.findByMovieId(MOVIE_ID))
                .thenReturn(Optional.empty());

        // 의도적으로 3차원 반환
        when(embeddingService.embedMovie(movie))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        AiMovieEmbeddingSyncService.SyncResult result =
                syncService.syncMovies(List.of(movie));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isEqualTo(1);

        verify(embeddingRepository, never())
                .upsert(
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(float[].class)
                );
    }

    @Test
    void 영화_목록이_null이면_예외가_발생한다() {

        assertThatThrownBy(() ->
                syncService.syncMovies(null)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("영화 목록은 null일 수 없습니다.");

        verifyNoInteractions(
                candidateService,
                embeddingService,
                embeddingRepository
        );
    }

    // 모든 테스트에서 공통으로 사용하는 영화 ID
    private AiMovieCandidate createMovie() {

        AiMovieCandidate movie = mock(AiMovieCandidate.class);

        when(movie.movieId()).thenReturn(MOVIE_ID);

        return movie;
    }

    // 정상 임베딩은 768차원
    private float[] createVector() {

        float[] vector = new float[DIMENSIONS];

        vector[0] = 0.1f;
        vector[1] = 0.2f;
        vector[2] = 0.3f;

        return vector;
    }

    private String sha256(String text)
            throws NoSuchAlgorithmException {

        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                text.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }
}