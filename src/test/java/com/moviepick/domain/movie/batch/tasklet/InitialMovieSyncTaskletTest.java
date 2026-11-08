package com.moviepick.domain.movie.batch.tasklet;

import com.moviepick.domain.movie.dto.response.MovieSyncResponse;
import com.moviepick.domain.movie.service.MovieCollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 초기 대량 수집 Tasklet: 설정한 페이지 수로 초기 수집을 부르고, 실패·중단이 있으면 배치를 실패시킨다
class InitialMovieSyncTaskletTest {

    private final MovieCollectionService collectionService = mock(MovieCollectionService.class);
    private final InitialMovieSyncTasklet tasklet = new InitialMovieSyncTasklet(collectionService, 100);

    @Test
    void 모두_저장되면_FINISHED() {
        when(collectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 2000, 0));

        assertThat(tasklet.execute(null, null)).isEqualTo(RepeatStatus.FINISHED);
        verify(collectionService).collectInitialPopularMoviesWithResult(100);
    }

    @Test
    void 한_편이라도_실패하면_예외() {
        when(collectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 1999, 1));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("TMDB 초기 영화 수집 실패: ");
    }

    @Test
    void 중단_페이지가_있으면_예외() {
        when(collectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 400, 0, 21, null));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 중단_사유만_있어도_예외() {
        when(collectionService.collectInitialPopularMoviesWithResult(100))
                .thenReturn(new MovieSyncResponse(100, 400, 0, null, "TMDB 응답 오류"));

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
