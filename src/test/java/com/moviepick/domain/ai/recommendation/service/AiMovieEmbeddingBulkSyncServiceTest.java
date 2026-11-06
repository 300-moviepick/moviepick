package com.moviepick.domain.ai.recommendation.service;

import com.moviepick.domain.ai.recommendation.dto.AiMovieCandidate;
import com.moviepick.domain.ai.recommendation.repository.AiMovieEmbeddingBatchReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingBulkSyncServiceTest {

    @Mock
    private AiMovieEmbeddingBatchReader batchReader;

    @Mock
    private AiMovieEmbeddingSyncService syncService;

    @InjectMocks
    private AiMovieEmbeddingBulkSyncService bulkSyncService;

    @Test
    void 영화_ID를_기준으로_페이지를_순서대로_조회한다() {

        List<AiMovieCandidate> firstPage = List.of(
                movie(1L),
                movie(5L)
        );

        List<AiMovieCandidate> secondPage = List.of(
                movie(11L),
                movie(21L)
        );

        given(batchReader.findNext(0L, 2))
                .willReturn(firstPage);

        given(batchReader.findNext(5L, 2))
                .willReturn(secondPage);

        given(batchReader.findNext(21L, 2))
                .willReturn(List.of());

        given(syncService.syncMovies(firstPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        2, 2, 0, 0, 0
                ));

        given(syncService.syncMovies(secondPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        2, 1, 0, 1, 0
                ));

        var result = bulkSyncService.sync(2, 10);

        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(4);
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.lastMovieId()).isEqualTo(21L);

        verify(batchReader).findNext(0L, 2);
        verify(batchReader).findNext(5L, 2);
        verify(batchReader).findNext(21L, 2);
    }

    @Test
    void 최대_처리_개수에_도달하면_중단한다() {

        List<AiMovieCandidate> firstPage = List.of(
                movie(1L),
                movie(5L)
        );

        List<AiMovieCandidate> secondPage = List.of(
                movie(11L)
        );

        given(batchReader.findNext(0L, 2))
                .willReturn(firstPage);

        // 남은 처리 가능 개수가 1이므로 다음 조회 크기는 1
        given(batchReader.findNext(5L, 1))
                .willReturn(secondPage);

        given(syncService.syncMovies(firstPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        2, 2, 0, 0, 0
                ));

        given(syncService.syncMovies(secondPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        1, 1, 0, 0, 0
                ));

        var result = bulkSyncService.sync(2, 3);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.lastMovieId()).isEqualTo(11L);

        verify(batchReader, times(2))
                .findNext(anyLong(), anyInt());
    }

    @Test
    void 일부_영화가_실패해도_다음_페이지를_처리한다() {

        List<AiMovieCandidate> firstPage = List.of(
                movie(1L),
                movie(5L)
        );

        List<AiMovieCandidate> secondPage = List.of(
                movie(11L)
        );

        given(batchReader.findNext(0L, 2))
                .willReturn(firstPage);

        given(batchReader.findNext(5L, 2))
                .willReturn(secondPage);

        given(batchReader.findNext(11L, 2))
                .willReturn(List.of());

        given(syncService.syncMovies(firstPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        2, 1, 0, 0, 1
                ));

        given(syncService.syncMovies(secondPage))
                .willReturn(new AiMovieEmbeddingSyncService.SyncResult(
                        1, 1, 0, 0, 0
                ));

        var result = bulkSyncService.sync(2, 10);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);

        verify(syncService).syncMovies(firstPage);
        verify(syncService).syncMovies(secondPage);
    }

    @Test
    void 조회할_영화가_없으면_정상_종료한다() {

        given(batchReader.findNext(0L, 50))
                .willReturn(List.of());

        var result = bulkSyncService.sync(50, 2000);

        assertThat(result.batches()).isZero();
        assertThat(result.total()).isZero();
        assertThat(result.created()).isZero();
        assertThat(result.failed()).isZero();

        verifyNoInteractions(syncService);
    }

    @Test
    void 잘못된_설정은_거부한다() {

        assertThatThrownBy(() -> bulkSyncService.sync(0, 100))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> bulkSyncService.sync(101, 100))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> bulkSyncService.sync(50, 0))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(batchReader, syncService);
    }

    private AiMovieCandidate movie(Long id) {
        return new AiMovieCandidate(
                id,
                id + 1000,
                "테스트 영화 " + id,
                "테스트 줄거리",
                LocalDate.of(2025, 1, 1),
                List.of("SF"),
                8.0,
                100.0,
                null
        );
    }
}