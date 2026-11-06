package com.moviepick.domain.ai.recommendation.scheduler;

import com.moviepick.domain.ai.recommendation.service.AiMovieEmbeddingBulkSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMovieEmbeddingScheduledSyncTest {

    @Mock
    private AiMovieEmbeddingBulkSyncService bulkSyncService;

    private AiMovieEmbeddingScheduledSync scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AiMovieEmbeddingScheduledSync(
                bulkSyncService,
                50,
                10000
        );
    }

    @Test
    void 예약_실행시_기존_대량_동기화_서비스를_호출한다() {

        given(bulkSyncService.sync(50, 10000))
                .willReturn(result(
                        2, 78, 0, 0, 78, 0, 100L
                ));

        assertThatCode(
                scheduler::synchronize
        ).doesNotThrowAnyException();

        verify(bulkSyncService, times(1))
                .sync(50, 10000);
    }

    @Test
    void 변경된_영화가_있으면_동기화_결과를_정상_처리한다() {

        given(bulkSyncService.sync(50, 10000))
                .willReturn(result(
                        2, 78, 0, 1, 77, 0, 100L
                ));

        assertThatCode(
                scheduler::synchronize
        ).doesNotThrowAnyException();

        verify(bulkSyncService)
                .sync(50, 10000);
    }

    @Test
    void 일부_영화가_실패하면_실패를_알린다() {

        given(bulkSyncService.sync(50, 10000))
                .willReturn(result(
                        2, 78, 0, 0, 77, 1, 100L
                ));

        assertThatThrownBy(
                scheduler::synchronize
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");

        verify(bulkSyncService)
                .sync(50, 10000);
    }

    @Test
    void 동기화_예외_후에도_다음_실행이_가능하다() {

        given(bulkSyncService.sync(50, 10000))
                .willThrow(
                        new IllegalStateException("DB 연결 오류")
                )
                .willReturn(result(
                        2, 78, 0, 0, 78, 0, 100L
                ));

        assertThatThrownBy(
                scheduler::synchronize
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 연결 오류");

        // 실패 후 실행 상태가 해제되어야 함
        assertThatCode(
                scheduler::synchronize
        ).doesNotThrowAnyException();

        verify(bulkSyncService, times(2))
                .sync(50, 10000);
    }

    @Test
    void 잘못된_배치_설정은_거부한다() {

        assertThatThrownBy(() ->
                new AiMovieEmbeddingScheduledSync(
                        bulkSyncService,
                        0,
                        10000
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new AiMovieEmbeddingScheduledSync(
                        bulkSyncService,
                        101,
                        10000
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                new AiMovieEmbeddingScheduledSync(
                        bulkSyncService,
                        50,
                        0
                )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private AiMovieEmbeddingBulkSyncService.BulkSyncResult result(
            int batches,
            int total,
            int created,
            int updated,
            int skipped,
            int failed,
            long lastMovieId
    ) {
        return new AiMovieEmbeddingBulkSyncService.BulkSyncResult(
                batches,
                total,
                created,
                updated,
                skipped,
                failed,
                lastMovieId
        );
    }
}